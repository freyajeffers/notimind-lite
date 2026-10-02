#!/usr/bin/env python3
"""Single-depth crawl of every course page + linked/attached file for FALL_TERM.

For each course listed in ``.tmp_compare/fall_2026_raw.json`` (or any course list
discoverable through the canonical ``courses`` endpoint), this script:

1. Lists every wiki page (Canvas ``GET /api/v1/courses/{id}/pages``).
2. Fetches the body of each page (HTML sanitised to Markdown) and writes the
   raw JSON plus a clean Markdown companion next to it.
3. Extracts every link from each page's HTML body and:
   - Same-host Canvas file URLs (courses/files/... or files API) -> cookie-auth
     download with the existing token-vault session.
   - External URLs (slide.blob.core.windows.net, drive.google.com, vendor
     docs, YouTube, etc.) -> optional Firecrawl passthrough when
     ``FIRECRAWL_API_URL`` + ``FIRECRAWL_API_KEY`` are set, else direct httpx
     fetch with a clear UA.
4. Walks every module (Canvas ``GET /courses/{id}/modules``) and ingests the
   ``File`` items so lecture slides / syllabi / worksheets surface even when no
   wiki page links to them.
5. Walks ``GET /courses/{id}/files`` so files uploaded straight to Files (no
   module association) are still picked up.

**All crawled resources land as Markdown** so AI agents have one canonical
ingestion format:

* Wiki pages / module Page items / front page / syllabus body -> ``.md``
* PDF / DOCX / text files -> binary + ``.md`` companion (PDF text wrapped in
  code fences, DOCX paragraphs as Markdown, plain text fenced when long).
* Image files (lecture slides, whiteboards, photos) -> binary + ``.ocr.md``
  companion when Tesseract OCR succeeds; otherwise the binary stands alone
  with a ``.metadata.json`` note explaining the absence.
* External HTML pages -> ``.external.md`` companion (raw HTML is also saved
  so external tools can re-render).

Files are extracted to plain text where possible:

* ``text/*``  : decoded with the charset declared in Content-Type.
* HTML       : sanitised via BeautifulSoup.
* PDF        : optional ``pypdf`` import; raises a clear error if missing.
* DOCX       : python-docx (already in the venv).
* Images     : saved verbatim; OCR deliberately skipped (no pytesseract
                in the venv and we don't want surprise system deps).
* Other      : saved verbatim alongside a ``.metadata.json`` "unsupported
                extraction" note so the agent can decide what to do.

Hard ceiling is 250 MB per file (``--max-file-size``). Anything over the cap
or locked / hidden / in error state is skipped with a placeholder
``.metadata.json`` that records the reason; the crawl continues.

Everything lands under ``<context_root>/fall-term/pages/<course_slug>/...`` and
``<context_root>/fall-term/files/<course_slug>/...``. ``<context_root>`` is
``$CANVAS_CONTEXT_ROOT`` if set, otherwise
``~/.local/share/canvas_mcp/context`` (next to the AES-256-GCM token vault so
the MCP server can serve it later).

Re-runs are resume-safe: each file is keyed by SHA-256 of its URL + size
header. If a previous run produced identical bytes the file is left untouched.

Usage:
    .venv/bin/python scripts/crawl_course_pages.py
    .venv/bin/python scripts/crawl_course_pages.py --courses 100001,100002
    .venv/bin/python scripts/crawl_course_pages.py --max-file-size 52428800
    .venv/bin/python scripts/crawl_course_pages.py --use-firecrawl

Exits non-zero only on hard failures (vault empty, no courses discovered).
Per-course or per-file errors are logged and the crawl continues.
"""

from __future__ import annotations

import argparse
import asyncio
import contextlib
import hashlib
import html as html_lib
import json
import logging
import mimetypes
import os
import random
import re
import socket
import sys
import tempfile
import time
from collections.abc import Iterable
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urljoin, urlparse

# Make the package importable when invoked directly from the repo root.
_REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(_REPO_ROOT / "src"))
sys.path.insert(0, str(_REPO_ROOT / "scripts"))

import httpx  # noqa: E402
from bs4 import BeautifulSoup  # noqa: E402

from canvas_mcp.auth.token_vault import TokenVault  # noqa: E402
from canvas_mcp.config import ServerConfig  # noqa: E402
from canvas_mcp.ipc import ipc_socket_from_env  # noqa: E402
from canvas_mcp.transport.client import PrimaryTransportProxy  # noqa: E402

LOGGER = logging.getLogger("crawl_course_pages")


def atomic_write_text(path: Path, content: str, *, encoding: str = "utf-8") -> None:
    """Publish text atomically so readers never observe a truncated artifact."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".part", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding=encoding) as handle:
            handle.write(content)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp_name, path)
    except BaseException:
        with contextlib.suppress(FileNotFoundError):
            os.unlink(tmp_name)
        raise


# Output roots --------------------------------------------------------------
DEFAULT_CONTEXT_ROOT = Path.home() / ".local" / "share" / "canvas_mcp" / "context"

# Hard ceilings (overridable via CLI) ---------------------------------------
DEFAULT_MAX_FILE_SIZE = 250 * 1024 * 1024  # 250 MB
DEFAULT_MAX_HTML_BODY = 4_000  # canvas-mcp invariant
REQUEST_TIMEOUT = httpx.Timeout(45.0, connect=15.0)
USER_AGENT = "canvas-mcp-crawler/1.0 (+https://github.com/freyajeffers/canvas-mcp)"

# Mimetypes we try to extract text from without third-party deps.
TEXTISH = re.compile(r"^(text/|application/(json|xml|javascript|x-shellscript|x-yaml))")
# Canvas serves file downloads via three URL shapes; all need the cookie header.
# 1. /api/v1/courses/{cid}/files/{fid} -> JSON metadata; the JSON includes the
#    real `url` we then fetch.
# 2. /courses/{cid}/files/{fid}/download -> 302 to instfs / S3.
# 3. /courses/{cid}/files/{fid}/preview -> same as download, served inline.
# 4. /courses/{cid}/files/{fid}?wrap=1 (HTML page wrapper) -> also resolves to
#    one of the above when followed.
CANVAS_FILE_PATH_RX = re.compile(r"/api/v1/(?:courses/\d+/)?files/\d+")
CANVAS_FILE_DOWNLOAD_RX = re.compile(r"/files/\d+/(?:download|preview)\b")
CANVAS_FILE_PAGE_RX = re.compile(r"/courses/\d+/files/\d+(?:[/?#]|$)")
CANVAS_ASSIGNMENT_RX = re.compile(r"/courses/(\d+)/assignments/(\d+)(?:[/?#]|$)")

# External hosts we always send to Firecrawl when configured (best-effort list).
EXTERNAL_HOST_HINTS = (
    "slide.blob.core.windows.net",
    "docs.google.com",
    "drive.google.com",
    "youtube.com",
    "youtu.be",
    "vimeo.com",
)

# Slugs and URL keywords for institutional policies / honor pledges to ignore
POLICY_SLUG_OR_URL_PATTERNS = re.compile(
    r"(academic[-_]standards|student[-_]conduct|plagiarism|uam[-_]?6502|honor[-_]pledge|academic[-_]integrity|academic[-_]dishonesty|ai[-_]policy)",
    re.IGNORECASE,
)


def _migrate_legacy_txt_companions(root: Path) -> int:
    """Rename any stale ``*.txt`` companion to ``*.md`` from prior runs.

    Older versions of the crawler wrote ``{stem}.txt`` text extractions. The
    current contract is ``{stem}.md``. We rename only files whose sibling
    binary exists, so we never destroy a real ``.txt`` source file.
    Returns the number of files migrated (useful for logging).
    """
    if not root.exists():
        return 0
    migrated = 0
    for txt in root.rglob("*.txt"):
        # Companions live next to their binary: foo.pdf.pdf.txt -> foo.pdf.pdf.md.
        # Strip only the trailing ".txt" and append ".md" so we keep the
        # full canonical name (which often itself has a duplicate extension
        # like ".pdf.pdf" when Canvas returns both extension + a duplicate).
        stem_with_ext = txt.name[: -len(".txt")]
        binary = txt.with_name(stem_with_ext)
        if not binary.exists():
            continue
        md_target = txt.with_name(stem_with_ext + ".md")
        if md_target.exists():
            continue
        try:
            txt.rename(md_target)
            migrated += 1
        except OSError as exc:  # pragma: no cover - defensive
            LOGGER.debug("legacy migrate skipped %s: %s", txt, exc)
    return migrated


def _migrate_legacy_firecrawl_outputs(root: Path) -> int:
    """Rename ``*.firecrawl.json`` companions to ``*.firecrawl.metadata.json``.

    Pre-firecrawl-passthrough runs wrote the Firecrawl raw payload next to the
    markdown file. The current contract is ``*.firecrawl.metadata.json`` so the
    metadata sidecar matches the ``*.metadata.json`` convention used elsewhere.
    """
    if not root.exists():
        return 0
    migrated = 0
    for legacy in root.rglob("*.firecrawl.json"):
        target = legacy.with_name(
            legacy.name.replace(".firecrawl.json", ".firecrawl.metadata.json")
        )
        if target.exists():
            continue
        try:
            legacy.rename(target)
            migrated += 1
        except OSError as exc:  # pragma: no cover - defensive
            LOGGER.debug("firecrawl migrate skipped %s: %s", legacy, exc)
    return migrated


def _migrate_legacy_layout(root: Path, new_term_slug: str) -> bool:
    """Move the pre-term-bucket ``<root>/fall-term/`` into ``<root>/<term>/``.

    The previous layout wrote every corpus under a hardcoded ``fall-term/``
    bucket. After we added term selection, the new layout names the bucket
    after the active term slug (typically ``fall-term`` but possibly
    ``2027-spring`` once we cross the term boundary). This migration moves the
    legacy dir into the new location so existing pages + files reappear
    without a full re-download.

    Returns ``True`` when anything was moved.
    """
    if new_term_slug in {"", "unknown"}:
        return False
    legacy = root / "fall-term"
    target = root / new_term_slug
    if not legacy.exists() or legacy == target:
        return False
    if target.exists():
        # Target already has fresh data; bail rather than clobber it.
        LOGGER.debug("legacy layout: %s already exists; not moving %s", target, legacy)
        return False
    try:
        legacy.rename(target)
        LOGGER.info("legacy layout: moved %s -> %s", legacy, target)
        return True
    except OSError as exc:  # pragma: no cover - defensive
        LOGGER.debug("legacy layout move failed: %s", exc)
        return False


# ---------------------------------------------------------------------------
# Vault + cookie helpers (reuse the refresh script's proven shape).
# ---------------------------------------------------------------------------
def load_vault_cookies(config: ServerConfig) -> tuple[str, str]:
    """Reuse the same vault-decryption as the rest of the server."""
    vault = TokenVault(
        config.canvas_token_vault_path,
        passphrase=config.canvas_vault_passphrase,
        salt_path=config.canvas_vault_salt_path,
    )
    data = vault.load_credentials() or {}
    return data.get("session_cookie", ""), data.get("csrf_token", "")


def canvas_headers(cookie: str, csrf: str) -> dict[str, str]:
    """Headers for every Canvas API call (matches the existing fetcher)."""
    return {
        "Cookie": (
            f"_legacy_normandy_session={cookie}; canvas_session={cookie}; _csrf_token={csrf}"
        ),
        "Accept": "application/json",
        "X-Requested-With": "XMLHttpRequest",
        "X-CSRF-Token": csrf,
        "User-Agent": USER_AGENT,
    }


# ---------------------------------------------------------------------------
# HTML/Markdown sanitisation (kept local; the package version lives elsewhere).
# ---------------------------------------------------------------------------
def html_to_markdown(body: str) -> str:
    """Best-effort HTML -> Markdown for a Canvas page body.

    Falls back to a stripped plain-text view when BeautifulSoup returns nothing
    useful (e.g. plain text inside <pre> tags). The canvas-mcp server uses a
    stricter pipeline; this is just enough fidelity for AI-agent ingestion.
    """
    if not body:
        return ""
    soup = BeautifulSoup(body, "html.parser")
    for tag in soup(["script", "style", "noscript", "iframe"]):
        tag.decompose()

    chunks: list[str] = []
    for el in soup.find_all(["h1", "h2", "h3", "h4", "p", "li", "pre", "blockquote"]):
        text = " ".join(el.get_text(" ").split())
        if not text:
            continue
        name = el.name
        if name == "h1":
            chunks.append(f"\n# {text}\n")
        elif name == "h2":
            chunks.append(f"\n## {text}\n")
        elif name == "h3":
            chunks.append(f"\n### {text}\n")
        elif name == "h4":
            chunks.append(f"\n#### {text}\n")
        elif name == "li":
            chunks.append(f"- {text}")
        elif name == "pre":
            chunks.append(f"\n```\n{text}\n```\n")
        elif name == "blockquote":
            chunks.append(f"> {text}")
        else:
            chunks.append(text)

    out = "\n\n".join(chunks).strip()
    if not out:
        out = soup.get_text(" ", strip=True)
    if len(out) > DEFAULT_MAX_HTML_BODY * 25:  # ~100KB worth before sentence clamp
        out = out[: DEFAULT_MAX_HTML_BODY * 25]
    return html_lib.unescape(out)


def _wrap_text_as_markdown(raw: str, *, heading: str | None) -> str:
    """Wrap a free-form text blob in a Markdown envelope.

    Plain text / DOCX paragraphs become Markdown with a single H1 heading
    (when ``heading`` is provided) and fenced code blocks for any line that
    looks like it was monospace in the source. Long single-line text gets
    paragraph-broken at sentence boundaries so the agent gets readable chunks
    instead of a single wall of text.
    """
    if not raw:
        return ""
    text = raw.replace("\r\n", "\n").replace("\r", "\n").strip()
    if not text:
        return ""
    # If the source has obvious paragraph breaks (blank lines), respect them.
    if "\n\n" in text:
        chunks = [c.strip() for c in text.split("\n\n") if c.strip()]
        body = "\n\n".join(chunks)
    else:
        # Wrap into ~1.5KB paragraphs at sentence boundaries so an agent that
        # truncates the file still gets coherent context.
        body = _paragraph_break(text, target=1500)
    if heading:
        return f"# {heading}\n\n{body}\n"
    return body + "\n"


def _paragraph_break(text: str, *, target: int) -> str:
    """Split ``text`` into paragraphs of ~``target`` chars at sentence ends."""
    if len(text) <= target:
        return text
    out: list[str] = []
    cursor = 0
    n = len(text)
    while cursor < n:
        end = min(cursor + target, n)
        if end < n:
            # Walk back to the nearest sentence terminator.
            for boundary in (". ", "! ", "? ", "\n"):
                idx = text.rfind(boundary, cursor, end)
                if idx > cursor + target // 2:
                    end = idx + len(boundary)
                    break
        chunk = text[cursor:end].strip()
        if chunk:
            out.append(chunk)
        cursor = end
    return "\n\n".join(out)


def extract_links(html_body: str, page_url: str) -> list[str]:
    """Pull every href from a page body, resolved against the page URL."""
    if not html_body:
        return []
    soup = BeautifulSoup(html_body, "html.parser")
    found: list[str] = []
    for tag in soup.find_all(["a", "img", "iframe", "source"]):
        for attr in ("href", "src"):
            raw = tag.get(attr)
            if not raw:
                continue
            # BeautifulSoup returns list[str] for duplicated attrs; normalise.
            raw_str = raw if isinstance(raw, str) else (raw[0] if raw else "")
            if not raw_str:
                continue
            absolute = urljoin(page_url, raw_str)
            parsed = urlparse(absolute)
            if parsed.scheme in {"http", "https"}:
                found.append(absolute)
    # dedupe, preserve order
    seen: set[str] = set()
    out: list[str] = []
    for u in found:
        if u not in seen:
            seen.add(u)
            out.append(u)
    return out


# ---------------------------------------------------------------------------
# Disk layout helpers.
# ---------------------------------------------------------------------------
def course_slug(course: dict[str, Any]) -> str:
    """Stable, filesystem-safe slug for a course row."""
    code = (course.get("course_code") or "").strip().lower()
    code = re.sub(r"[^a-z0-9]+", "_", code).strip("_") or "course"
    cid = course.get("id") or course.get("course_id") or 0
    return f"course-{cid}-{code}"


def term_slug(term: dict[str, Any] | None) -> str:
    """Stable, filesystem-safe slug for a Canvas term.

    Examples:
        {"name": "FALL_TERM"}    -> "fall-term"
        {"name": "Spring 2025"}  -> "spring-2025"
        None                     -> "unknown"
    """
    if not term or not isinstance(term, dict):
        return "unknown"
    name = (term.get("name") or "").strip().lower()
    if not name:
        return "unknown"
    slug = re.sub(r"[^a-z0-9]+", "-", name).strip("-")
    return slug or "unknown"


def parse_term_date(value: Any) -> float | None:
    """Parse a Canvas ISO date (or unix float / int) into a unix timestamp.

    Returns ``None`` for missing / unparseable values so the term-selection
    logic can skip them without crashing.
    """
    if value is None or value == "":
        return None
    if isinstance(value, (int, float)):
        try:
            return float(value)
        except (TypeError, ValueError):
            return None
    text = str(value).strip()
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        pass
    try:
        dt = datetime.fromisoformat(text.replace("Z", "+00:00"))
        return dt.timestamp()
    except (TypeError, ValueError):
        return None


def pick_latest_term(
    courses: list[dict[str, Any]], *, now: float | None = None
) -> tuple[dict[str, Any] | None, str]:
    """Select the term the user is most likely operating in.

    Strategy (in order):
    1. The term whose ``[start_at, end_at]`` covers ``now`` (default: today).
       If multiple terms cover today, prefer the one whose ``end_at`` is
       furthest in the future (the longest-running current term).
    2. The term with the latest ``start_at`` (upcoming term).
    3. ``None`` (caller writes ``unknown/`` and continues).

    Returns ``(term_dict_or_None, term_slug_string)``.
    """
    if not courses:
        return None, "unknown"
    moment = float(now if now is not None else datetime.now().timestamp())
    # Group by term dict identity (Canvas reuses term objects across courses).
    seen_terms: dict[int, dict[str, Any]] = {}
    for c in courses:
        term = c.get("term")
        if isinstance(term, dict) and term.get("id") is not None:
            seen_terms.setdefault(int(term["id"]), term)

    in_progress: list[tuple[float, dict[str, Any]]] = []
    upcoming: list[tuple[float, dict[str, Any]]] = []
    for term in seen_terms.values():
        start = parse_term_date(term.get("start_at"))
        end = parse_term_date(term.get("end_at"))
        if start is not None and end is not None and start <= moment <= end:
            in_progress.append((end, term))
        elif start is not None and start > moment:
            upcoming.append((start, term))

    if in_progress:
        in_progress.sort(key=lambda t: t[0], reverse=True)
        return in_progress[0][1], term_slug(in_progress[0][1])
    if upcoming:
        upcoming.sort(key=lambda t: t[0], reverse=True)
        return upcoming[0][1], term_slug(upcoming[0][1])
    # Fallback: most recent past term by start_at.
    dated: list[tuple[float, dict[str, Any]]] = []
    for term in seen_terms.values():
        start = parse_term_date(term.get("start_at"))
        if start is not None:
            dated.append((start, term))
    if dated:
        dated.sort(key=lambda t: t[0], reverse=True)
        return dated[0][1], term_slug(dated[0][1])
    return None, "unknown"


def safe_filename(name: str, max_len: int = 180) -> str:
    name = re.sub(r"[\\/:*?\"<>|]+", "_", name)
    name = re.sub(r"\s+", " ", name).strip()
    if len(name) > max_len:
        stem, dot, ext = name.rpartition(".")
        if dot and len(ext) < 10:
            keep = max_len - len(ext) - 1
            name = stem[:keep] + "." + ext
        else:
            name = name[:max_len]
    return name or "file"


def content_disposition_filename(headers: httpx.Headers, url: str) -> str:
    cd = headers.get("Content-Disposition", "")
    match = re.search(r"filename\*?=(?:UTF-8''|\")?([^\";]+)", cd, re.I)
    if match:
        return safe_filename(unquote(match.group(1)))
    return safe_filename(unquote(Path(urlparse(url).path).name) or "file")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


# ---------------------------------------------------------------------------
# Per-crawl session state.
# ---------------------------------------------------------------------------
@dataclass
class CrawlStats:
    courses: int = 0
    pages: int = 0
    module_pages: int = 0
    syllabus_pages: int = 0
    page_files: int = 0
    module_files: int = 0
    module_externals: int = 0
    course_files: int = 0
    external_files: int = 0
    assignments: int = 0
    bytes_downloaded: int = 0
    skipped: list[dict[str, str]] = field(default_factory=list)


@dataclass
class Crawler:
    """Bundles API client and optional authenticated browser fallback."""

    config: ServerConfig
    base_url: str
    cookie: str
    csrf: str
    client: httpx.AsyncClient
    context_root: Path
    max_file_size: int
    use_firecrawl: bool
    firecrawl_url: str | None
    firecrawl_key: str | None
    browser_context: Any | None = None
    api_transport: Any | None = None
    term_slug: str = "unknown"
    stats: CrawlStats = field(default_factory=CrawlStats)

    # ------------------------------------------------------------------ paths
    def term_root(self) -> Path:
        """Per-term context root; all pages + files live under here."""
        d = self.context_root / self.term_slug
        d.mkdir(parents=True, exist_ok=True)
        return d

    def pages_dir(self, slug: str) -> Path:
        d = self.term_root() / "pages" / slug
        d.mkdir(parents=True, exist_ok=True)
        return d

    def files_dir(self, slug: str) -> Path:
        d = self.term_root() / "files" / slug
        d.mkdir(parents=True, exist_ok=True)
        return d

    def index_path(self) -> Path:
        return self.term_root() / "_index.json"

    # -------------------------------------------------------------- internal
    def _canon_firecrawl(self) -> bool:
        return bool(self.use_firecrawl and self.firecrawl_url and self.firecrawl_key)

    # ----------------------------------------------------------------- fetch
    async def _get_json(self, path_or_url: str, params: dict[str, Any] | None = None) -> Any:
        if self.api_transport is not None and not path_or_url.startswith("http"):
            return await self.api_transport.get(path_or_url, params=params, follow_pages=True)
        url = path_or_url if path_or_url.startswith("http") else f"{self.base_url}{path_or_url}"
        for attempt in range(1, 6):
            try:
                resp = await self.client.get(
                    url, headers=canvas_headers(self.cookie, self.csrf), params=params
                )
                resp.raise_for_status()
                return resp.json()
            except Exception as exc:
                transient = isinstance(exc, socket.gaierror) or (
                    isinstance(exc, httpx.HTTPError)
                    and ("Name or service not known" in str(exc) or "temporarily" in str(exc).lower())
                )
                if not transient or attempt == 5:
                    raise
                delay = 0.2 * (2 ** (attempt - 1)) * random.uniform(0.8, 1.2)
                LOGGER.warning("transient Canvas request failure (%d/5) url=%s: %r; retrying in %.2fs", attempt, url, exc, delay)
                await asyncio.sleep(delay)

    async def _paginate(self, path: str, params: dict[str, Any]) -> list[dict[str, Any]]:
        """Walk RFC 5988 Link headers for a Canvas collection endpoint."""
        out: list[dict[str, Any]] = []
        page = 1
        while True:
            qp = {**params, "page": page, "per_page": params.get("per_page", 100)}
            payload = await self._get_json(path, qp)
            if not isinstance(payload, list):
                return out + ([payload] if isinstance(payload, dict) else [])
            out.extend(payload)
            if len(payload) < int(qp["per_page"]):
                return out
            page += 1

    # ------------------------------------------------------------- ingest file
    async def _download(
        self, url: str, dest: Path, *, max_size: int | None = None
    ) -> dict[str, Any]:
        """Stream a file to disk with a hard size cap.

        Returns a small metadata dict; on failure raises so the caller can
        record a placeholder.
        """
        cap = max_size if max_size is not None else self.max_file_size
        # Same-host downloads use the Canvas cookie; external URLs do not.
        is_canvas = urlparse(url).netloc == urlparse(self.base_url).netloc
        headers = (
            canvas_headers(self.cookie, self.csrf) if is_canvas else {"User-Agent": USER_AGENT}
        )
        tmp = dest.with_suffix(dest.suffix + ".part")
        sha = hashlib.sha256()
        total = 0
        try:
            async with self.client.stream("GET", url, headers=headers) as resp:
                resp.raise_for_status()
                ctype = resp.headers.get("Content-Type", "application/octet-stream")
                clen = resp.headers.get("Content-Length")
                if clen and clen.isdigit() and int(clen) > cap:
                    raise ValueError(f"file exceeds cap ({int(clen)} > {cap} bytes)")
                with tmp.open("wb") as fh:
                    async for chunk in resp.aiter_bytes(64 * 1024):
                        total += len(chunk)
                        if total > cap:
                            raise ValueError(f"file exceeds cap during stream ({total} > {cap})")
                        sha.update(chunk)
                        fh.write(chunk)
            tmp.replace(dest)
        except Exception:
            with contextlib.suppress(FileNotFoundError):
                tmp.unlink()
            raise
        return {
            "bytes": total,
            "sha256": sha.hexdigest(),
            "content_type": ctype,
        }

    async def _browser_download(self, url: str, dest: Path) -> dict[str, Any]:
        """Download a student-visible Canvas file through the logged-in browser.

        Student sessions can open module-linked files while Canvas denies the
        Files API. Playwright follows Canvas's signed storage redirect and
        captures the browser download without exposing cookie values to logs.
        """
        if self.browser_context is None:
            raise RuntimeError("authenticated browser fallback is unavailable")
        from playwright.async_api import Error as PlaywrightError

        target = url
        parsed = urlparse(url)
        if re.search(r"/files/\d+(?:$|[?])", parsed.path):
            target = urljoin(url, parsed.path.rstrip("/") + "/download?download_frd=1")
        page = await self.browser_context.new_page()
        suggested_name: str | None = None
        try:
            async with page.expect_download(timeout=30_000) as download_info:
                try:
                    await page.goto(target, wait_until="commit", timeout=30_000)
                except PlaywrightError:
                    # Playwright raises for navigation that immediately starts
                    # a download; the Download event is the successful result.
                    pass
            download = await download_info.value
            suggested_name = download.suggested_filename
            await download.save_as(str(dest))
        finally:
            await page.close()
        total = dest.stat().st_size
        if total > self.max_file_size:
            dest.unlink(missing_ok=True)
            raise ValueError(f"file exceeds cap during browser download ({total} > {self.max_file_size})")
        digest = hashlib.sha256(dest.read_bytes()).hexdigest()
        actual_path = dest
        if not dest.suffix and suggested_name and Path(suggested_name).suffix:
            actual_path = dest.with_name(safe_filename(suggested_name))
            dest.replace(actual_path)
        return {
            "bytes": total,
            "sha256": digest,
            "content_type": mimetypes.guess_type(actual_path.name)[0] or "application/octet-stream",
            "path": str(actual_path),
        }

    async def _extract_text(self, dest: Path, content_type: str) -> str | None:
        """Best-effort Markdown extraction; returns None for binary / unsupported.

        Every returned string is Markdown-shaped (headings, fences, lists) so
        downstream AI agents ingest one canonical format regardless of whether
        the source was PDF, DOCX, plain text, or HTML. Returns ``None`` for
        content we cannot extract -- the binary is still saved so an external
        tool can re-process it later.
        """
        ctype = (content_type or "").split(";")[0].strip().lower()
        stem = dest.stem
        if TEXTISH.match(ctype) and dest.suffix.lower() not in {".html", ".htm"}:
            try:
                raw = dest.read_text(encoding="utf-8", errors="replace")
            except Exception as exc:  # pragma: no cover
                LOGGER.debug("text read failed for %s: %s", dest, exc)
                return None
            return _wrap_text_as_markdown(raw, heading=stem)
        if ctype == "text/html" or dest.suffix.lower() in {".html", ".htm"}:
            try:
                raw = dest.read_text(encoding="utf-8", errors="replace")
            except Exception:
                return None
            body_md = html_to_markdown(raw)
            return f"# {stem}\n\n{body_md}\n"
        if ctype == "application/pdf" or dest.suffix.lower() == ".pdf":
            from canvas_mcp.pdf_ingestion import ingest_pdf

            try:
                return await asyncio.to_thread(ingest_pdf, dest)
            except Exception as exc:
                LOGGER.warning("PDF ingestion failed for %s: %s", dest, type(exc).__name__)
                return None
        if dest.suffix.lower() == ".docx":
            try:
                import docx  # type: ignore[import-not-found]
            except ImportError:
                return None
            try:
                d = docx.Document(str(dest))  # type: ignore[attr-defined]
                paras = [p.text for p in d.paragraphs if p.text]
                if not paras:
                    return None
                body = "\n\n".join(paras)
                return f"# {stem}\n\n{_wrap_text_as_markdown(body, heading=None)}\n"
            except Exception as exc:  # pragma: no cover
                LOGGER.debug("python-docx failed on %s: %s", dest, exc)
                return None
        return None

    # ----------------------------------------------------------- ingest page
    async def ingest_page(
        self,
        course: dict[str, Any],
        page: dict[str, Any],
        *,
        origin_tag: str | None = None,
    ) -> dict[str, Any]:
        cid = course["id"]
        url_slug = page.get("url") or page.get("page_id") or page.get("title")
        slug = safe_filename(str(url_slug)).lower()

        # Skip academic integrity / honor pledge / institutional policy pages
        if POLICY_SLUG_OR_URL_PATTERNS.search(slug) or POLICY_SLUG_OR_URL_PATTERNS.search(
            str(page.get("title", ""))
        ):
            LOGGER.info("[page] skipping policy/pledge page: %s (%s)", url_slug, page.get("title"))
            return {"skipped": True, "reason": "policy/pledge page"}

        body_path = self.pages_dir(course_slug(course)) / f"{slug}.md"
        meta_path = self.pages_dir(course_slug(course)) / f"{slug}.json"
        page_origin = origin_tag or f"page:{url_slug}"

        # Reuse cache when remote body matches the previous hash.
        try:
            detail = await self._get_json(f"/api/v1/courses/{cid}/pages/{url_slug}")
        except Exception as exc:
            LOGGER.warning("[page] %s/%s fetch failed: %s", cid, url_slug, exc)
            self.stats.skipped.append(
                {"kind": "page", "id": f"{cid}/{url_slug}", "reason": str(exc)}
            )
            atomic_write_text(
                meta_path,
                json.dumps(
                    {"page": page, "course_id": cid, "skipped": True, "reason": str(exc)},
                    indent=2,
                ),
            )
            return {"skipped": True, "reason": str(exc)}

        body = detail.get("body") or ""
        markdown = html_to_markdown(body)
        page_url = detail.get("html_url") or f"{self.base_url}/courses/{cid}/pages/{url_slug}"

        atomic_write_text(body_path, f"# {detail.get('title') or url_slug}\n\n{markdown}\n")
        meta = {
            "course_id": cid,
            "page_id": detail.get("page_id"),
            "url_slug": url_slug,
            "title": detail.get("title"),
            "html_url": page_url,
            "updated_at": detail.get("updated_at"),
            "body_sha256": sha256_bytes(body.encode("utf-8")),
            "extracted_chars": len(markdown),
            "links": extract_links(body, page_url),
            "origin": page_origin,
        }
        atomic_write_text(meta_path, json.dumps(meta, indent=2))
        self.stats.pages += 1
        LOGGER.info(
            "[page] %s/%s chars=%d links=%d origin=%s",
            cid,
            url_slug,
            len(markdown),
            len(meta["links"]),
            page_origin,
        )
        # Now crawl every link found inside the page (single depth).
        for link in meta["links"]:
            try:
                await self.ingest_link(course, link, origin=f"page:{url_slug}")
            except Exception as exc:
                LOGGER.warning("[link] %s ingest failed: %s", link, exc)
                self.stats.skipped.append({"kind": "link", "url": link, "reason": str(exc)})
        return meta

    async def ingest_external_or_canvas_file(
        self, course: dict[str, Any], url: str, *, origin: str
    ) -> None:
        """Dispatch helper for module External URL / External Tool items.

        Same-host Canvas URLs route through ``ingest_canvas_file``; everything
        else routes through ``ingest_external`` (Firecrawl or direct httpx).
        """
        parsed = urlparse(url)
        is_canvas = parsed.netloc == urlparse(self.base_url).netloc
        assignment_match = CANVAS_ASSIGNMENT_RX.search(parsed.path) if is_canvas else None
        if assignment_match:
            await self.ingest_assignment(
                course,
                int(assignment_match.group(1)),
                int(assignment_match.group(2)),
                origin=origin,
            )
            return
        if is_canvas:
            await self.ingest_canvas_file(course, url, origin=origin)
        else:
            await self.ingest_external(course, url, origin=origin)
            self.stats.module_externals += 1

    # --------------------------------------------------------- ingest helpers
    async def ingest_link(self, course: dict[str, Any], url: str, *, origin: str) -> None:
        if POLICY_SLUG_OR_URL_PATTERNS.search(url):
            LOGGER.info("[link] skipping policy link: %s", url)
            return

        parsed = urlparse(url)
        is_canvas = parsed.netloc == urlparse(self.base_url).netloc

        assignment_match = CANVAS_ASSIGNMENT_RX.search(parsed.path) if is_canvas else None
        if assignment_match:
            await self.ingest_assignment(
                course,
                int(assignment_match.group(1)),
                int(assignment_match.group(2)),
                origin=origin,
            )
            return

        # Canvas /files/{id} or /files/{id}/download -> straight to downloader
        if is_canvas and (
            CANVAS_FILE_DOWNLOAD_RX.search(parsed.path)
            or CANVAS_FILE_PATH_RX.search(parsed.path)
            or CANVAS_FILE_PAGE_RX.search(parsed.path)
        ):
            await self.ingest_canvas_file(course, url, origin=origin)
            return

        # External URL: passthrough Firecrawl when configured, else direct httpx.
        if not is_canvas:
            await self.ingest_external(course, url, origin=origin)
            return

        # Canvas but not a file API path: skip (e.g. another page URL); the
        # listing pass above already covers pages.
        LOGGER.debug("[link] skipping non-file canvas url: %s", url)

    async def ingest_assignment(
        self,
        course: dict[str, Any],
        course_id: int,
        assignment_id: int,
        *,
        origin: str,
    ) -> None:
        """Persist an assignment from the Canvas API without HTML navigation."""
        assignment = await self._get_json(
            f"/api/v1/courses/{course_id}/assignments/{assignment_id}"
        )
        if not isinstance(assignment, dict):
            raise ValueError(f"assignment {assignment_id} returned non-object payload")
        root = self.files_dir(course_slug(course)) / "assignments"
        root.mkdir(parents=True, exist_ok=True)
        stem = safe_filename(f"{assignment_id}-{assignment.get('name') or 'assignment'}")
        description = assignment.get("description") or ""
        markdown = html_to_markdown(description) if description else ""
        title = str(assignment.get("name") or f"Assignment {assignment_id}")
        body = f"# {title}\n\n{markdown}\n" if markdown else f"# {title}\n"
        atomic_write_text(root / f"{stem}.md", body)
        metadata = {
            "course_id": course_id,
            "assignment_id": assignment_id,
            "name": title,
            "html_url": assignment.get("html_url"),
            "due_at": assignment.get("due_at"),
            "points_possible": assignment.get("points_possible"),
            "submission_types": assignment.get("submission_types"),
            "workflow_state": assignment.get("workflow_state"),
            "body_sha256": sha256_bytes(description.encode("utf-8")),
            "extracted_chars": len(markdown),
            "origin": origin,
            "source": "canvas_api",
        }
        atomic_write_text(root / f"{stem}.metadata.json", json.dumps(metadata, indent=2))
        self.stats.assignments += 1
        LOGGER.info("[assignment] %s/%s -> %s", course_id, assignment_id, root / f"{stem}.md")

    async def _resolve_canvas_file(self, url: str) -> tuple[dict[str, Any], str]:
        """Return (metadata, download_url) for any Canvas file URL.

        Handles four URL shapes (see CANVAS_FILE_*_RX). The metadata dict is
        empty when the URL doesn't resolve to JSON.
        """
        parsed = urlparse(url)
        # Direct API endpoint -> JSON metadata; use the embedded url field.
        if CANVAS_FILE_PATH_RX.search(parsed.path):
            meta = await self._get_json(url)
            dl = meta.get("url") or meta.get("download_url") or url
            return meta, dl
        # Page-style /files/{fid}?wrap=1 or bare -> ask the API for metadata.
        if CANVAS_FILE_PAGE_RX.search(parsed.path):
            m = re.search(r"/files/(\d+)", parsed.path)
            if m:
                fid = m.group(1)
                # The course is embedded in the URL path (/courses/{cid}/files/{fid}).
                cm = re.search(r"/courses/(\d+)/", parsed.path)
                if cm:
                    api = f"/api/v1/courses/{cm.group(1)}/files/{fid}"
                else:
                    api = f"/api/v1/files/{fid}"
                meta = await self._get_json(api)
                dl = meta.get("url") or meta.get("download_url") or url
                return meta, dl
        # Direct /download or /preview path -> metadata via API if possible.
        m = re.search(r"/files/(\d+)", parsed.path)
        if m and CANVAS_FILE_DOWNLOAD_RX.search(parsed.path):
            fid = m.group(1)
            cm = re.search(r"/courses/(\d+)/", parsed.path)
            try:
                if cm:
                    api = f"/api/v1/courses/{cm.group(1)}/files/{fid}"
                else:
                    api = f"/api/v1/files/{fid}"
                meta = await self._get_json(api)
                return meta, url
            except Exception:
                return {}, url
        return {}, url

    async def ingest_canvas_file(self, course: dict[str, Any], url: str, *, origin: str) -> None:
        files_root = self.files_dir(course_slug(course))
        try:
            meta, download_url = await self._resolve_canvas_file(url)
        except Exception as exc:
            if self.browser_context is not None:
                LOGGER.info("[canvas-file] API denied; using browser fallback for %s", url)
                meta, download_url = {}, url
            else:
                LOGGER.warning("[canvas-file] resolve failed for %s: %s", url, exc)
                self.stats.skipped.append({"kind": "canvas_file", "url": url, "reason": str(exc)})
                self._write_skip(
                    files_root,
                    safe_filename(unquote(Path(urlparse(url).path).name) or "file"),
                    reason=str(exc),
                    meta={"course_id": course.get("id"), "url": url, "origin": origin},
                )
                return

        display = (
            meta.get("display_name")
            or meta.get("filename")
            or unquote(Path(urlparse(url).path).name)
            or "file"
        )
        size_hint = int(meta.get("size") or 0)
        if size_hint and size_hint > self.max_file_size:
            self._write_skip(
                files_root,
                safe_filename(display),
                reason=f"size {size_hint} > cap {self.max_file_size}",
                meta={
                    "course_id": course.get("id"),
                    "url": url,
                    "resolved_url": download_url,
                    "origin": origin,
                    "expected_size": size_hint,
                    "content_type": meta.get("content-type") or meta.get("content_type"),
                },
            )
            self.stats.skipped.append({"kind": "canvas_file", "url": url, "reason": "over cap"})
            return

        # The Canvas API's display_name often includes the file extension
        # (e.g. "20260824.pdf"), so avoid appending ext again in that case.
        has_ext = bool(Path(display).suffix)
        ext = (
            Path(display).suffix
            if has_ext
            else mimetypes.guess_extension((meta.get("content-type") or "").split(";")[0].strip())
            or ""
        )
        dest = files_root / safe_filename(display + ext)
        if dest.exists() and dest.stat().st_size > 0:
            if dest.suffix.lower() == ".pdf":
                await self._extract_text(dest, "application/pdf")
            if origin.startswith("page:"):
                self.stats.page_files += 1
            return

        info: dict[str, Any] | None = None
        try:
            info = await self._download(download_url, dest)
        except Exception as exc:
            if self.browser_context is not None:
                try:
                    LOGGER.info("[canvas-file] HTTP download failed; using browser fallback for %s", url)
                    info = await self._browser_download(url, dest)
                    dest = Path(info.get("path", str(dest)))
                except Exception as browser_exc:
                    exc = browser_exc
            if info is None:
                self._write_skip(
                    files_root,
                    safe_filename(display),
                    reason=str(exc),
                    meta={
                        "course_id": course.get("id"),
                        "url": url,
                        "resolved_url": download_url,
                        "origin": origin,
                    },
                )
                self.stats.skipped.append({"kind": "canvas_file", "url": url, "reason": str(exc)})
                return

        text_md: str | None = None
        ocr_md: str | None = None
        if self._is_image(dest, info["content_type"]):
            ocr_md = await self._ocr_image(dest)
            if ocr_md is not None:
                atomic_write_text(dest.parent / f"{dest.name}.ocr.md", ocr_md)
        else:
            text_md = await self._extract_text(dest, info["content_type"])
            if text_md is not None:
                atomic_write_text(dest.parent / f"{dest.name}.md", text_md)
        atomic_write_text(dest.parent / f"{dest.name}.metadata.json",
            json.dumps(
                {
                    "course_id": course.get("id"),
                    "url": url,
                    "resolved_url": download_url,
                    "origin": origin,
                    "display_name": display,
                    "content_type": info["content_type"],
                    "size_bytes": info["bytes"],
                    "sha256": info["sha256"],
                    "extracted_markdown_path": f"{dest.name}.md" if text_md else None,
                    "ocr_markdown_path": f"{dest.name}.ocr.md" if ocr_md else None,
                    "extracted_chars": (
                        len(text_md) if text_md else (len(ocr_md) if ocr_md else 0)
                    ),
                    "canvas_file_id": meta.get("id"),
                },
                indent=2,
            )
        )
        if origin.startswith("page:"):
            self.stats.page_files += 1
        elif origin.startswith("module:"):
            self.stats.module_files += 1
        elif origin.startswith("course-files:"):
            self.stats.course_files += 1
        self.stats.bytes_downloaded += info["bytes"]
        LOGGER.info("[file] canvas %s -> %s (%d B)", download_url, dest, info["bytes"])

    @staticmethod
    def _is_image(dest: Path, content_type: str) -> bool:
        ctype = (content_type or "").split(";")[0].strip().lower()
        return ctype.startswith("image/") or dest.suffix.lower() in {
            ".png",
            ".jpg",
            ".jpeg",
            ".gif",
            ".webp",
            ".tif",
            ".tiff",
            ".bmp",
        }

    async def _ocr_image(self, dest: Path) -> str | None:
        """Run Tesseract OCR over an image file; returns Markdown or None.

        Returns ``None`` when ``pytesseract`` is missing, the Tesseract binary
        isn't installed, or the image can't be decoded. The binary is still
        saved by the caller; the agent gets a ``.metadata.json`` note about
        why OCR was skipped.
        """
        try:
            import pytesseract  # type: ignore[import-not-found]
            from PIL import Image  # type: ignore[import-not-found]
        except ImportError as exc:
            LOGGER.debug("[ocr] dependencies missing for %s: %s", dest, exc)
            return None
        try:
            with Image.open(dest) as img:
                text = pytesseract.image_to_string(img)
        except Exception as exc:
            LOGGER.debug("[ocr] tesseract failed on %s: %s", dest, exc)
            return None
        text = (text or "").strip()
        if not text:
            return None
        body = _wrap_text_as_markdown(text, heading=None)
        return f"# OCR: {dest.stem}\n\n{body}\n"

    async def ingest_external(self, course: dict[str, Any], url: str, *, origin: str) -> None:
        # Defensive guard: assignment links can arrive here from module
        # external-tool payloads or extracted HTML links. Never request their
        # HTML wrapper; Canvas returns the usable representation via REST.
        assignment_match = CANVAS_ASSIGNMENT_RX.search(urlparse(url).path)
        if assignment_match and urlparse(url).netloc == urlparse(self.base_url).netloc:
            await self.ingest_assignment(
                course,
                int(assignment_match.group(1)),
                int(assignment_match.group(2)),
                origin=origin,
            )
            return
        files_root = self.files_dir(course_slug(course))
        # Firecrawl passthrough: scrape + download returned assets if available.
        if self._canon_firecrawl() and any(h in url for h in EXTERNAL_HOST_HINTS):
            await self._ingest_via_firecrawl(course, url, files_root, origin=origin)
            return

        # Direct httpx for everything else.
        try:
            head = await self.client.head(url, follow_redirects=True)
        except Exception as exc:
            LOGGER.debug("[external] HEAD failed for %s: %s", url, exc)
            head = None  # type: ignore[assignment]
        clen = head.headers.get("Content-Length") if head is not None else None
        if clen and clen.isdigit() and int(clen) > self.max_file_size:
            self._write_skip(
                files_root,
                safe_filename(unquote(Path(urlparse(url).path).name) or "external"),
                reason=f"size {clen} > cap {self.max_file_size}",
                meta={"course_id": course.get("id"), "url": url, "origin": origin},
            )
            self.stats.skipped.append({"kind": "external", "url": url, "reason": "over cap"})
            return

        ext = Path(urlparse(url).path).suffix
        dest = files_root / safe_filename(
            (unquote(Path(urlparse(url).path).name) or "external") + ext
        )
        if dest.exists() and dest.stat().st_size > 0:
            if dest.suffix.lower() == ".pdf":
                await self._extract_text(dest, "application/pdf")
            self.stats.external_files += 1
            return
        try:
            info = await self._download(url, dest)
        except Exception as exc:
            self._write_skip(
                files_root,
                safe_filename(unquote(Path(urlparse(url).path).name) or "external"),
                reason=str(exc),
                meta={"course_id": course.get("id"), "url": url, "origin": origin},
            )
            self.stats.skipped.append({"kind": "external", "url": url, "reason": str(exc)})
            return
        atomic_write_text(dest.parent / f"{dest.name}.metadata.json",
            json.dumps(
                {
                    "course_id": course.get("id"),
                    "url": url,
                    "origin": origin,
                    "size_bytes": info["bytes"],
                    "sha256": info["sha256"],
                    "content_type": info["content_type"],
                },
                indent=2,
            )
        )
        text_md: str | None = None
        ocr_md: str | None = None
        if self._is_image(dest, info["content_type"]):
            ocr_md = await self._ocr_image(dest)
            if ocr_md is not None:
                atomic_write_text(dest.parent / f"{dest.name}.ocr.md", ocr_md)
        else:
            text_md = await self._extract_text(dest, info["content_type"])
            if text_md is not None:
                atomic_write_text(dest.parent / f"{dest.name}.md", text_md)
        # Patch the metadata we already wrote so extracted_markdown_path / ocr
        # paths reflect the actual companion we just dropped.
        meta_path = dest.parent / f"{dest.name}.metadata.json"
        meta = json.loads(meta_path.read_text()) if meta_path.exists() else {}
        meta["extracted_markdown_path"] = f"{dest.name}.md" if text_md else None
        meta["ocr_markdown_path"] = f"{dest.name}.ocr.md" if ocr_md else None
        meta["extracted_chars"] = len(text_md) if text_md else (len(ocr_md) if ocr_md else 0)
        atomic_write_text(meta_path, json.dumps(meta, indent=2))
        self.stats.external_files += 1
        self.stats.bytes_downloaded += info["bytes"]
        LOGGER.info("[file] external %s -> %s (%d B)", url, dest, info["bytes"])

    async def _ingest_via_firecrawl(
        self,
        course: dict[str, Any],
        url: str,
        files_root: Path,
        *,
        origin: str,
    ) -> None:
        """Hit the configured Firecrawl /v2/scrape and persist markdown + assets."""
        assert self.firecrawl_url and self.firecrawl_key
        endpoint = f"{self.firecrawl_url.rstrip('/')}/v2/scrape"
        try:
            resp = await self.client.post(
                endpoint,
                headers={
                    "Authorization": f"Bearer {self.firecrawl_key}",
                    "Content-Type": "application/json",
                },
                json={
                    "url": url,
                    "formats": ["markdown", "links"],
                    "onlyMainContent": True,
                },
                timeout=60.0,
            )
            resp.raise_for_status()
        except Exception as exc:
            self._write_skip(
                files_root,
                safe_filename(unquote(Path(urlparse(url).path).name) or "firecrawl"),
                reason=f"firecrawl scrape failed: {exc}",
                meta={"course_id": course.get("id"), "url": url, "origin": origin},
            )
            self.stats.skipped.append({"kind": "external", "url": url, "reason": str(exc)})
            return

        slug = safe_filename(url.split("://", 1)[-1]).replace(".", "_")[:120]
        md_dest = files_root / f"{slug}.firecrawl.md"
        meta_dest = files_root / f"{slug}.firecrawl.metadata.json"
        payload = (
            resp.json()
            if resp.headers.get("content-type", "").startswith("application/json")
            else {"raw": resp.text}
        )
        data = payload.get("data") if isinstance(payload, dict) else None
        markdown = (data or {}).get("markdown") if isinstance(data, dict) else None
        if not markdown and isinstance(payload, dict):
            markdown = payload.get("markdown")
        md_dest.write_text(markdown or json.dumps(payload, indent=2))
        meta_dest.write_text(
            json.dumps({"course_id": course.get("id"), "url": url, "origin": origin}, indent=2)
        )
        self.stats.external_files += 1
        LOGGER.info("[firecrawl] %s -> %s", url, md_dest)

    # -------------------------------------------------------------- walking
    async def crawl_course(self, course: dict[str, Any]) -> None:
        cid = course["id"]
        LOGGER.info("=== course %s (%s) ===", cid, course.get("course_code"))

        # 1. Wiki pages. Some courses disable the wiki feature, so the listing
        # endpoint returns 404 with a "disabled" message. Fall back to fetching
        # the front page when available, then move on.
        pages: list[dict[str, Any]] = []
        try:
            pages = await self._paginate(
                f"/api/v1/courses/{cid}/pages",
                {"per_page": 100},
            )
        except httpx.HTTPStatusError as exc:
            body = (exc.response.text or "").lower() if exc.response is not None else ""
            if "disabled" in body or exc.response is None or exc.response.status_code in (404, 403):
                LOGGER.info("[pages] wiki disabled for %s; trying front page", cid)
                try:
                    front = await self._get_json(f"/api/v1/courses/{cid}/front_page")
                    if isinstance(front, dict) and front.get("url"):
                        pages = [front]
                except Exception as fexc:
                    LOGGER.info("[pages] no front page for %s: %s", cid, fexc)
            else:
                LOGGER.warning("[pages] listing failed for %s: %s", cid, exc)
        except Exception as exc:
            LOGGER.warning("[pages] listing failed for %s: %s", cid, exc)

        for page in pages:
            try:
                await self.ingest_page(course, page)
            except Exception as exc:
                LOGGER.warning("[pages] %s failed: %s", page.get("url"), exc)
                self.stats.skipped.append(
                    {"kind": "page", "id": str(page.get("url")), "reason": str(exc)}
                )

        # 1b. Syllabus body. Some courses don't use the wiki feature at all
        # but still publish the syllabus inline on the course root. The
        # syllabus_body field is HTML; we sanitise and write it as a page
        # named "syllabus" so it gets the same Markdown treatment.
        syllabus_body = course.get("syllabus_body")
        if syllabus_body and isinstance(syllabus_body, str) and syllabus_body.strip():
            try:
                slug = "syllabus"
                body_path = self.pages_dir(course_slug(course)) / f"{slug}.md"
                meta_path = self.pages_dir(course_slug(course)) / f"{slug}.json"
                if not body_path.exists():
                    markdown = html_to_markdown(syllabus_body)
                    body_path.write_text(f"# Syllabus\n\n{markdown}\n")
                    meta = {
                        "course_id": cid,
                        "url_slug": "syllabus",
                        "title": "Syllabus",
                        "html_url": f"{self.base_url}/courses/{cid}/assignments/syllabus",
                        "body_sha256": sha256_bytes(syllabus_body.encode("utf-8")),
                        "extracted_chars": len(markdown),
                        "links": extract_links(syllabus_body, f"{self.base_url}/courses/{cid}"),
                        "origin": "course:syllabus",
                    }
                    atomic_write_text(meta_path, json.dumps(meta, indent=2))
                    self.stats.syllabus_pages += 1
                    self.stats.pages += 1
                    LOGGER.info("[syllabus] %s chars=%d", cid, len(markdown))
                # Single-depth link walk for syllabus too.
                for link in extract_links(syllabus_body, f"{self.base_url}/courses/{cid}"):
                    try:
                        await self.ingest_link(course, link, origin="page:syllabus")
                    except Exception as exc:
                        LOGGER.warning("[link] %s ingest failed: %s", link, exc)
            except Exception as exc:
                LOGGER.warning("[syllabus] %s failed: %s", cid, exc)

        # 2. Module File items (lecture slides often live here).
        try:
            modules = await self._paginate(
                f"/api/v1/courses/{cid}/modules",
                {"per_page": 100},
            )
        except Exception as exc:
            LOGGER.warning("[modules] listing failed for %s: %s", cid, exc)
            modules = []
        for module in modules:
            try:
                items = await self._paginate(
                    f"/api/v1/courses/{cid}/modules/{module['id']}/items",
                    {"per_page": 100},
                )
            except Exception as exc:
                LOGGER.warning("[modules] items failed for %s: %s", module.get("id"), exc)
                continue
            for item in items:
                item_type = (item.get("type") or "").lower()
                if item_type == "file":
                    url = item.get("url") or ""
                    if not url:
                        continue
                    try:
                        await self.ingest_canvas_file(
                            course, url, origin=f"module:{module.get('id')}:{item.get('id')}"
                        )
                    except Exception as exc:
                        LOGGER.warning("[module-file] %s failed: %s", url, exc)
                elif item_type == "page":
                    # Module Page items expose a wiki page by URL slug via the
                    # item.url (e.g. "/courses/100001/pages/home-page") or via
                    # the page_id field; either way we treat it as a page walk.
                    page_url = item.get("url") or item.get("page_url") or ""
                    page_id = item.get("page_id")
                    page_handle = page_id or page_url.split("/pages/")[-1] if page_url else None
                    if not page_handle:
                        continue
                    try:
                        await self.ingest_page(
                            course,
                            {
                                "url": page_handle,
                                "page_id": page_id,
                                "title": item.get("title"),
                            },
                            origin_tag=f"module:{module.get('id')}:{item.get('id')}",
                        )
                        self.stats.module_pages += 1
                    except Exception as exc:
                        LOGGER.warning("[module-page] %s/%s failed: %s", cid, page_handle, exc)
                elif item_type == "assignment":
                    assignment_id = item.get("content_id") or item.get("id")
                    if assignment_id:
                        try:
                            await self.ingest_assignment(
                                course,
                                int(cid),
                                int(assignment_id),
                                origin=f"module:{module.get('id')}:{item.get('id')}",
                            )
                        except Exception as exc:
                            LOGGER.warning("[module-assignment] %s/%s failed: %s", cid, assignment_id, exc)
                elif item_type in {"external_url", "externaltool"}:
                    # Module External URL / External Tool items surface lecture
                    # links (YouTube, vendor docs, etc.) that wouldn't otherwise
                    # be reachable from the front page or wiki body.
                    url = item.get("external_url") or item.get("url") or ""
                    if not url:
                        continue
                    try:
                        await self.ingest_external_or_canvas_file(
                            course, url, origin=f"module:{module.get('id')}:{item.get('id')}"
                        )
                    except Exception as exc:
                        LOGGER.warning("[module-external] %s failed: %s", url, exc)

        # 3. Course-wide /files listing. As a student we may get a 403 here;
        # fall back to /users/self/files filtered by this course, then to the
        # course-level endpoint with ?use_auth=true the existing canvas-mcp
        # server uses. Falls back to a no-op log line so the crawl continues.
        files: list[dict[str, Any]] = []
        try:
            files = await self._paginate(
                f"/api/v1/courses/{cid}/files",
                {"per_page": 100},
            )
        except httpx.HTTPStatusError as exc:
            status = exc.response.status_code if exc.response is not None else 0
            if status in (401, 403):
                LOGGER.info(
                    "[files] course /files forbidden for %s; falling back to user files",
                    cid,
                )
                try:
                    user_files = await self._paginate(
                        "/api/v1/users/self/files",
                        {"per_page": 100},
                    )
                except Exception as fexc:
                    LOGGER.info("[files] user files fallback failed for %s: %s", cid, fexc)
                    user_files = []
                files = [
                    f
                    for f in user_files
                    if str(f.get("context_id")) == str(cid)
                    and (f.get("context_type") or "").lower() == "course"
                ]
                LOGGER.info("[files] user-files cross-match for %s: %d", cid, len(files))
            else:
                LOGGER.warning("[files] listing failed for %s: %s", cid, exc)
        except Exception as exc:
            LOGGER.warning("[files] listing failed for %s: %s", cid, exc)

        for f in files:
            url = f.get("url") or ""
            if not url:
                continue
            try:
                await self.ingest_canvas_file(course, url, origin=f"course-files:{f.get('id')}")
            except Exception as exc:
                LOGGER.warning("[course-file] %s failed: %s", url, exc)

    def _write_skip(self, dest_dir: Path, name: str, *, reason: str, meta: dict[str, Any]) -> None:
        meta_path = dest_dir / f"{safe_filename(name)}.skip.json"
        meta_path.write_text(json.dumps({"reason": reason, **meta}, indent=2))


# ---------------------------------------------------------------------------
# Driver.
# ---------------------------------------------------------------------------
async def async_main(args: argparse.Namespace) -> int:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s %(message)s",
        stream=sys.stderr,
    )
    config = ServerConfig(canvas_emulation_enabled=True)
    cookie, csrf = load_vault_cookies(config)
    if not cookie or not csrf:
        LOGGER.error("Vault is missing cookie or CSRF token.")
        return 2
    # When emulation is enabled we prefer a longer default timeout to
    # accommodate slower Playwright-driven page loads.
    REQUEST_TIMEOUT = 60.0

    base = config.canvas_base_url.rstrip("/")
    context_root = Path(args.context_root).expanduser()
    context_root.mkdir(parents=True, exist_ok=True)

    fc_url = os.environ.get("FIRECRAWL_API_URL")
    fc_key = os.environ.get("FIRECRAWL_API_KEY")
    use_fc = args.use_firecrawl
    if use_fc and not (fc_url and fc_key):
        LOGGER.warning(
            "Firecrawl requested but FIRECRAWL_API_URL / FIRECRAWL_API_KEY unset; "
            "falling back to direct httpx for external URLs."
        )
        use_fc = False

    timeout = REQUEST_TIMEOUT
    async with httpx.AsyncClient(
        timeout=timeout,
        follow_redirects=True,
        trust_env=False,
        limits=httpx.Limits(max_connections=20, max_keepalive_connections=5, keepalive_expiry=5.0),
    ) as client:
        browser_context = None
        browser = None
        playwright = None
        api_transport = None
        ipc_path = ipc_socket_from_env()
        if ipc_path is not None:
            api_transport = PrimaryTransportProxy(config, ipc_path)
            LOGGER.info("using primary transport proxy for Canvas API discovery")
        crawler = Crawler(
            config=config,
            base_url=base,
            cookie=cookie,
            csrf=csrf,
            client=client,
            context_root=context_root,
            max_file_size=args.max_file_size,
            use_firecrawl=use_fc,
            firecrawl_url=fc_url,
            firecrawl_key=fc_key,
            browser_context=browser_context,
            api_transport=api_transport,
        )

        courses = await _load_courses(crawler, args)
        if not courses:
            LOGGER.error("No courses to crawl.")
            return 3
        # Browser fallback is opt-in. Starting a local Playwright browser merely
        # to discover that no CDP endpoint exists creates a large process tree
        # and can leak descriptors during repeated daemon runs.
        cdp_url = os.environ.get("CHROME_REMOTE_DEBUG_URL")
        if cdp_url:
            try:
                from playwright.async_api import async_playwright

                playwright = await async_playwright().start()
                browser = await playwright.chromium.connect_over_cdp(cdp_url)
                if browser.contexts:
                    browser_context = browser.contexts[0]
                    crawler.browser_context = browser_context
                    LOGGER.info("authenticated browser fallback connected via CDP")
            except Exception as exc:
                LOGGER.info("browser fallback unavailable: %s", type(exc).__name__)
                if playwright is not None:
                    await playwright.stop()
                    playwright = None
        else:
            LOGGER.info("browser fallback disabled: CHROME_REMOTE_DEBUG_URL is not configured")
        crawler.stats.courses = len(courses)

        # Pick the active term (today vs [start_at, end_at]) and bucket all
        # output under <context_root>/<term_slug>/.
        term, slug = pick_latest_term(courses)
        crawler.term_slug = slug
        if term:
            LOGGER.info(
                "active term: id=%s name=%s slug=%s",
                term.get("id"),
                term.get("name"),
                slug,
            )

        # Migrate legacy .txt companions -> .md before any new downloads
        # happen so a stale file doesn't shadow the freshly-written .md.
        context_root.mkdir(parents=True, exist_ok=True)
        migrated_layout = _migrate_legacy_layout(context_root, slug)
        migrated_txt = _migrate_legacy_txt_companions(context_root)
        migrated_fc = _migrate_legacy_firecrawl_outputs(context_root)
        if migrated_txt or migrated_fc or migrated_layout:
            LOGGER.info(
                "legacy migration: layout=%s, %d .txt->.md, %d firecrawl.json->.metadata.json",
                migrated_layout,
                migrated_txt,
                migrated_fc,
            )

        started = time.monotonic()
        for c in courses:
            try:
                await crawler.crawl_course(c)
            except Exception as exc:
                LOGGER.error("[course] %s fatal: %s", c.get("id"), exc)
                crawler.stats.skipped.append(
                    {"kind": "course", "id": str(c.get("id")), "reason": str(exc)}
                )
        elapsed = time.monotonic() - started
        if browser is not None:
            await browser.close()
        if playwright is not None:
            await playwright.stop()

    crawler.index_path().parent.mkdir(parents=True, exist_ok=True)
    crawler.index_path().write_text(
        json.dumps(
            {
                "ran_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                "elapsed_seconds": round(elapsed, 2),
                "context_root": str(context_root),
                "term": (
                    {
                        "id": term.get("id") if term else None,
                        "name": term.get("name") if term else None,
                        "slug": slug,
                        "start_at": term.get("start_at") if term else None,
                        "end_at": term.get("end_at") if term else None,
                    }
                    if term
                    else {"slug": slug}
                ),
                "firecrawl_enabled": crawler._canon_firecrawl(),
                "max_file_size": args.max_file_size,
                "courses": [c.get("id") for c in courses],
                "stats": {
                    "courses": crawler.stats.courses,
                    "pages": crawler.stats.pages,
                    "module_pages": crawler.stats.module_pages,
                    "syllabus_pages": crawler.stats.syllabus_pages,
                    "page_files": crawler.stats.page_files,
                    "module_files": crawler.stats.module_files,
                    "module_externals": crawler.stats.module_externals,
                    "course_files": crawler.stats.course_files,
                    "external_files": crawler.stats.external_files,
                    "bytes_downloaded": crawler.stats.bytes_downloaded,
                    "skipped_count": len(crawler.stats.skipped),
                },
                "skipped": crawler.stats.skipped,
            },
            indent=2,
        )
    )

    LOGGER.info(
        "DONE: %d courses | %d pages (%d module, %d syllabus) | %d assignments | %d files (%d page, %d module, %d course, %d external, %d module_ext) | %.1f MB downloaded | %d skipped | %.1fs | term=%s",
        crawler.stats.courses,
        crawler.stats.pages,
        crawler.stats.module_pages,
        crawler.stats.syllabus_pages,
        crawler.stats.assignments,
        crawler.stats.page_files
        + crawler.stats.module_files
        + crawler.stats.course_files
        + crawler.stats.external_files
        + crawler.stats.module_externals,
        crawler.stats.page_files,
        crawler.stats.module_files,
        crawler.stats.course_files,
        crawler.stats.external_files,
        crawler.stats.module_externals,
        crawler.stats.bytes_downloaded / 1_048_576,
        len(crawler.stats.skipped),
        elapsed,
        crawler.term_slug,
    )

    # Top-level index enumerating every term bucket the MCP server can read.
    # The most recent entry (by mtime) is what the server surfaces as the
    # "latest term" without needing to walk the filesystem on every request.
    top_index = context_root / "_index.json"
    buckets: list[dict[str, Any]] = []
    for child in sorted(context_root.iterdir()):
        if not child.is_dir() or child.name.startswith("."):
            continue
        idx = child / "_index.json"
        if not idx.exists():
            continue
        try:
            payload = json.loads(idx.read_text())
        except (OSError, ValueError):
            continue
        buckets.append(
            {
                "term_slug": child.name,
                "index_path": str(idx),
                "ran_at": payload.get("ran_at"),
                "courses": payload.get("courses", []),
                "stats": payload.get("stats", {}),
            }
        )
    buckets.sort(key=lambda b: b.get("ran_at") or "", reverse=True)
    top_index.write_text(
        json.dumps(
            {
                "ran_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                "context_root": str(context_root),
                "latest_term_slug": crawler.term_slug,
                "terms": buckets,
            },
            indent=2,
        )
    )

    return 0


async def _load_courses(crawler: Crawler, args: argparse.Namespace) -> list[dict[str, Any]]:
    if args.courses:
        ids = {int(x) for x in args.courses.split(",") if x.strip().isdigit()}
        out: list[dict[str, Any]] = []
        for cid in ids:
            try:
                # ``include[]=term`` makes the term dict present so term
                # selection works the same as the listing path.
                out.append(
                    await crawler._get_json(
                        f"/api/v1/courses/{cid}",
                        params={"include[]": ["term", "syllabus_body"]},
                    )
                )
            except Exception as exc:
                LOGGER.warning("[course-load] %s failed: %s", cid, exc)
        return out

    # Default: read .tmp_compare/fall_2026_raw.json if present, otherwise
    # paginate /courses?state[]=available.
    raw_path = _REPO_ROOT / ".tmp_compare" / "fall_2026_raw.json"
    if raw_path.exists():
        return list(json.loads(raw_path.read_text()))
    return await crawler._paginate(
        "/api/v1/courses",
        {
            "per_page": 100,
            "state[]": "available",
            "include[]": ["term", "syllabus_body"],
        },
    )


def parse_args(argv: Iterable[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--courses",
        type=str,
        default=None,
        help="Comma-separated Canvas course IDs to crawl (default: fall_2026_raw.json).",
    )
    p.add_argument(
        "--max-file-size",
        type=int,
        default=DEFAULT_MAX_FILE_SIZE,
        help="Hard per-file size cap in bytes (default: 250 MB).",
    )
    p.add_argument(
        "--context-root",
        type=Path,
        default=DEFAULT_CONTEXT_ROOT,
        help="Root directory for pages + files (default: ~/.local/share/canvas_mcp/context).",
    )
    p.add_argument(
        "--use-firecrawl",
        action="store_true",
        help="Pipe external URLs through Firecrawl when FIRECRAWL_API_URL + _KEY are set.",
    )
    return p.parse_args(list(argv) if argv is not None else None)


if __name__ == "__main__":
    raise SystemExit(asyncio.run(async_main(parse_args())))
