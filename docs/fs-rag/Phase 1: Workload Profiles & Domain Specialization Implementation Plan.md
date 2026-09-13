# Phase 1: Workload Profiles & Domain Specialization Implementation Plan

## Filesystem RAG MCP Server (Consolidated Roadmap)

### 1. Executive Summary & Architectural Scope

Phase 1 enhances the filesystem_rag_mcp configuration and ingestion subsystems to seamlessly support specialized user workspaces alongside full codebase operations. Rather than maintaining separate scripts or diverging codebases, this phase introduces a unified profile abstraction that allows the server to tailor its scanning rules, file format filters, editor artifact exclusions, and chunking heuristics based on the target domain.

The primary objective is to enable first-class support for a local Markdown personal notes vault (defaulting to ~/.local/share/notes) without degrading or altering the system's existing multi-format document conversion and code intelligence features.

### 2. Workload Profile Architecture

The configuration layer must define a polymorphic or extensible profile hierarchy:

- Profile Contract Specification: A standardized data contract defining:

- Profile identifier and descriptive name.

- Default filesystem root directory (with home directory and environment variable expansion).

- Allowed file extension patterns (whitelist) and glob rules.

- Ignored file patterns and directory exclusion lists.

- Chunking strategy selector (e.g., Markdown AST breadcrumb chunker vs. code syntax chunker vs. generic sliding window).

- Feature flags controlling symbol extraction, git history tracking, and format conversion.

- Personal Notes Profile:

- Target Path: Defaults to ~/.local/share/notes.

- Inclusion Scope: Strictly limited to Markdown files (*.md, *.markdown).

- Disabled Subsystems: Disables code symbol extraction, git repository analysis, and complex binary document conversion to minimize memory footprint and execution overhead.

- AST Breadcrumbs: Enforces heading breadcrumb retention (H1 through H6) so that extracted text chunks retain full parent contextual hierarchies.

- General Codebase Profile:

- Target Path: Configurable to any project workspace or repository root.

- Inclusion Scope: Broad multi-format support encompassing source code files, documentation, configuration files, and converted office documents.

- Active Subsystems: Enables tree-sitter symbol parsing, directory tree generation, regex grepping, and git branch/diff inspection.

- Runtime Profile Resolution: The active profile is resolved through a strict hierarchy of precedence: command-line arguments override environment variables, which in turn override user configuration files and system defaults.

### 3. Editor Artifact & Temporary File Filtering

Text editing workflows in terminal environments frequently create transient files that must be rejected before entering ingestion pipelines:

- Vim/Neovim Swap Files: Exclude files matching swap extensions and patterns (.*.swp, .*.swo, .*.swx).

- Persistent Undo Files: Exclude files matching undo patterns (.*.un~).

- Backup & Working Files: Exclude files ending with tildes (*~), temporary files (*.tmp, .*.tmp), and atomic write artifacts (e.g., .goutputstream-*).

- Dotfile & Hidden Directory Handling: Automatically exclude standard hidden metadata directories (e.g., .git/, .obsidian/, .trash/) while allowing the watcher to selectively monitor specific configured paths.

- Upstream Ingestion Guard: File path evaluation must occur immediately upon event detection to prevent allocation of queue memory or file descriptor handles for non-qualifying files.

### 4. Heading Breadcrumb Preservation in Chunk Metadata

To ensure high retrieval precision within large, hierarchical note files:

- AST Parsing Pipeline: Utilize an Abstract Syntax Tree parser (such as Mistune) to track document outline hierarchies during the chunking pass.

- Hierarchical Breadcrumbs: Maintain a contextual breadcrumb stack that records the active heading lineage (e.g., Project > Architecture > Database Storage) for every content block.

- Metadata Association: Attach the breadcrumb string directly to the chunk data structure and inject it into the SQLite FTS5 index, ensuring that lexical queries match both chunk body content and parent structural titles.

### 5. Diagnostics & Environment Validation (Doctor Enhancements)

The system diagnostic tool (doctor.py) must be extended to perform pre-flight verification:

- Verify that the active profile root directory exists, is readable, and has appropriate user permissions.

- Verify that database file paths are writable and residing on filesystems supporting SQLite WAL mode and memory mapping.

- Audit exclusion filter rules against sample filenames to confirm that editor artifacts are correctly filtered.

- Report total indexable file counts and estimated chunk quantities for the active profile.

### 6. Acceptance & Verification Criteria

- Switching between profiles via CLI flags or environment variables alters target paths and filter rules deterministically.

- Editing notes using Vim produces no database updates or orphan records from swap or backup files.

- Parsed Markdown chunks include valid heading breadcrumbs in their metadata payloads.

- Existing codebase features (symbols, git ops, multi-format converters) continue to operate cleanly when the general codebase profile is selected.

- The doctor diagnostic command accurately validates directory permissions and active profile configurations.
