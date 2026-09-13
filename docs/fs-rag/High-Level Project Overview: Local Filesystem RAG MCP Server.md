# High-Level Project Overview: Local Filesystem RAG MCP Server

## Offline-First Neural & Lexical Filesystem Intelligence via Model Context Protocol

### 1. Executive Summary & Mission

The Local Filesystem RAG MCP Server is a high-performance, privacy-first, offline system daemon designed to provide AI coding assistants and agents (such as Claude Desktop, Zed Editor, and Hermes Agent) with instantaneous semantic and lexical search across personal notes, documents, and codebases.

Operating strictly locally on Arch Linux without cloud dependencies, the server consolidates Abstract Syntax Tree (AST) Markdown parsing, dual-modality retrieval (SQLite FTS5 full-text search combined with FastEmbed dense vector embeddings), reciprocal rank fusion (RRF), and cross-encoder neural reranking into an automated background service.

### 2. Dual-Workload Architecture

The system seamlessly reconciles two distinct operational modes within a unified package architecture:

- Personal Notes Vault Mode: Dedicated to personal markdown knowledge vaults (defaulting to ~/.local/share/notes). In this mode, the system aggressively filters editor artifacts (such as Vim swap and undo files), parses Markdown AST structures to preserve heading breadcrumbs (H1 through H6), and disables heavy code analysis tools to maintain a near-zero memory footprint.

- General Codebase & Filesystem Mode: Provides expansive multi-format intelligence across software repositories. It includes tree-sitter code symbol extraction, regex-based grepping, file patch application, git commit/diff tracking, and multi-format document conversion (handling PDFs, office documents, and structured text).

### 3. End-to-End System Architecture

The architecture is partitioned into strictly decoupled layers:

- Transport & Protocol Layer: Implements the Model Context Protocol (MCP) using FastMCP over standard input/output (stdio), exposing clean tool primitives for search, indexing, inspection, and maintenance.

- Event-Driven Filesystem Ingestion: Utilizes an inotify-backed daemon (via watchfiles) with sliding debounce windows (500–1000 ms) to ingest file creations, modifications, renames, and deletions in near real time.

- Document Delta & Content Hashing: Evaluates cryptographic content hashes (BLAKE3 or SHA-256) before triggering text processing, short-circuiting unchanged files with zero CPU overhead.

- Dual-Stage Retrieval Pipeline:

- Stage 1 (Broad Recall): Queries SQLite FTS5 lexical index (BM25) and dense vector embeddings (cosine similarity) for top 50 candidates each, fusing results via Reciprocal Rank Fusion ($k=60$).

- Stage 2 (Cross-Encoder Reranking): Slices top 20 candidates from the RRF pool and evaluates query-chunk relevance using an ONNX cross-encoder model.

- Stage 3 (Final Projection): Returns the top 5 reranked chunks sorted by sigmoid probability scores.

- Query Caching: Maintains an in-memory cache of query embeddings and reranked responses to eliminate model evaluation for recurring queries.

- Storage Engine: A single, hardened SQLite database operating in Write-Ahead Logging (WAL) mode with 256 MB memory-mapped I/O and a 5000 ms busy timeout, ensuring non-blocking concurrent reads and writes.

### 4. Hardware Topology & Threading Architecture

The inference engine is engineered to exploit the host workstation's AMD Ryzen 5 5600XT processor (6 physical Zen 3 cores, 12 hardware threads, 32 MB unified L3 cache):

- Thread Pinning: ONNX Runtime execution provider options are pinned to intra_op_num_threads = 6 (matching physical cores) and inter_op_num_threads = 1, preventing thread starvation and avoiding hyperthreading penalties during heavy matrix multiplications.

- Session Isolation: Bi-encoder embedding and cross-encoder reranking sessions maintain isolated execution boundaries, preventing background indexing from interrupting interactive user queries.

- UI Responsiveness: Eliminates system micro-stutters and dropped frames during concurrent background file ingestion.

### 5. Consolidated 5-Phase Implementation Roadmap

- Phase 1: Workload Profiles & Domain Specialization: Introducing profile configuration, notes vault defaults, Vim artifact filtering, and heading breadcrumb preservation.

- Phase 2: Hardware-Conscious Inference & Adaptive Thread Pinning: Applying CPU thread limits, model warm-up passes, 3-stage retrieval funnel tuning, and query cache integration.

- Phase 3: Service Daemonization & Systemd Lifecycle: Deploying user-level systemd units, journald logging, lingering, and CLI management.

- Phase 4: Retrieval Quality Benchmarking & Regression Gates: Curating a 25-query ground-truth benchmark, tracking MRR@5/Hit@1, and enforcing sub-75ms latency budgets in CI.

- Phase 5: Automated Storage Maintenance & Operational Runbooks: Implementing SQLite incremental vacuuming, FTS5 segment optimization, and weekly systemd maintenance timers.

### 6. Technology Stack & Key Runtime Dependencies

- Runtime Environment: Arch Linux (x86_64), Python 3.12+.

- Package & Environment Manager: uv with pyproject.toml and pinned uv.lock.

- Core Frameworks: FastMCP (Anthropic MCP SDK), Mistune (AST Markdown parsing), Watchfiles (inotify bindings).

- Machine Learning & Inference: ONNX Runtime (CPUExecutionProvider), FastEmbed (BGE embedding models).

- Storage & Indexing: SQLite 3 with FTS5, BLOB vector storage, and WAL mode.

- Service Supervision: Systemd user instances and Journald.
