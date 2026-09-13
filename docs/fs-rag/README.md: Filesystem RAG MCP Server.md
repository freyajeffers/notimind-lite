# README.md: Filesystem RAG MCP Server

## Offline-First Hybrid Semantic & Lexical Filesystem Search via Model Context Protocol

Filesystem RAG MCP Server is a high-performance, privacy-first, local search daemon that connects AI coding assistants and agents (including Claude Desktop, Zed Editor, and Hermes Agent) directly to your local files, notes, and codebases.

Built for Arch Linux and running entirely on local hardware, it combines Abstract Syntax Tree (AST) Markdown parsing, SQLite FTS5 lexical matching, FastEmbed dense vector embeddings, reciprocal rank fusion (RRF), and ONNX Runtime cross-encoder neural reranking into an automated, low-overhead system daemon.

### 1. Key Features

- Dual-Workload Architecture:

- Personal Notes Vault Mode: Optimized for local Markdown vaults (defaulting to ~/.local/share/notes). Preserves hierarchical heading breadcrumbs (H1–H6) in chunk metadata, filters editor artifacts, and disables heavy code analysis tools for minimal memory overhead.

- General Codebase Mode: Full multi-format intelligence across software repositories, supporting code symbol extraction, regex grepping, git diff tracking, directory tree inspection, and document format conversions.

- Two-Stage Hybrid Retrieval Funnel:

- Stage 1 (Broad Recall): Queries SQLite FTS5 (BM25) and dense vector embeddings (cosine similarity) for top 50 candidates each, fused via Reciprocal Rank Fusion ($k=60$).

- Stage 2 (Neural Rerank Cut): Slices the top 20 candidates from the RRF pool and evaluates relevance using an ONNX cross-encoder model.

- Stage 3 (Final Projection): Returns the top 5 highest-scoring chunks sorted by sigmoid probability score.

- Event-Driven Background Synchronization: Real-time inotify file watcher (via watchfiles) with a sliding debounce window (500–1000 ms) to ingest file additions, edits, deletions, and moves.

- Vim & Editor Artifact Exclusion: Automatically ignores transient editor files, including swap files (.*.swp, .*.swo, .*.swx), persistent undo files (.*.un~), backup files (*~), and temporary write files.

- Zero-Overhead Content Hashing: Computes cryptographic hashes (BLAKE3 or SHA-256) prior to chunking and embedding, instantly short-circuiting unmodified files with zero CPU consumption.

- Hardened SQLite Storage Layer: Configured with Write-Ahead Logging (PRAGMA journal_mode = WAL;), a 5000 ms busy timeout, and 256 MB memory-mapped I/O (PRAGMA mmap_size = 268435456;) to allow non-blocking concurrent reads during active indexing.

- Query Result Caching: In-memory cache for query embeddings and reranking scores, enabling sub-millisecond responses for recurring searches.

- Autonomous Background Daemonization: Supervised by systemd user services (mcp-rag-watcher.service) on Arch Linux with automatic restarts and structured telemetry routed to journald.

### 2. Hardware Optimization & Threading Architecture

Engineered specifically to exploit the host workstation's AMD Ryzen 5 5600XT processor (6 physical Zen 3 cores, 12 execution threads, 32 MB unified L3 cache):

- Thread Pinning: ONNX Runtime execution provider settings are pinned to intra_op_num_threads = 6 (matching physical cores) and inter_op_num_threads = 1. This prevents thread starvation, eliminates hyperthreading overhead during matrix math, and guarantees zero UI micro-stutters.

- Session Isolation: Bi-encoder embedding and cross-encoder reranking sessions maintain isolated execution boundaries, preventing background indexing passes from competing with interactive search queries.

- Adaptive Fallbacks: Automatically detects physical core counts on non-target systems to prevent scheduler oversaturation.

### 3. MCP Tool Primitives

The FastMCP server exposes clean, strictly-typed tool interfaces over standard input/output (stdio):

- search_notes: Accepts query string, result count, and optional weights; returns top reranked chunks with breadcrumb metadata.

- index_file: Triggers immediate indexing of a specific file path.

- reindex_directory: Performs a full directory scan and reconciliation against disk state.

- get_index_stats: Returns indexed document counts, chunk quantities, database size, and sync timestamps.

- optimize_database: Triggers SQLite incremental vacuuming and FTS5 inverted index segment compaction.

- extract_symbols, grep_code, get_tree, apply_patch, git_status: Advanced codebase tools active in general codebase mode.

### 4. Quickstart & Installation

Prerequisites: Arch Linux workstation, Python 3.12+, and uv.

- Environment Setup: Create and synchronize the dedicated virtual environment:

- Target virtual environment: ~/.local/share/mcp-rag/venv

- Install dependencies: uv sync

- Initialize Notes Vault: Ensure the target notes directory exists:

- mkdir -p ~/.local/share/notes

- Run Diagnostics: Verify system configuration and directory permissions:

- python -m filesystem_rag_mcp.cli doctor

- Enable Background Daemon: Install and start the systemd user service:

- systemctl --user daemon-reload

- systemctl --user enable --now mcp-rag-watcher.service

- Enable persistent lingering: loginctl enable-linger $USER

### 5. Client Integration Configurations

#### Claude Desktop (~/.config/Claude/claude_desktop_config.json)

Configure the server executable to point directly to the isolated virtual environment Python interpreter:

- Command: /home/user/.local/share/mcp-rag/venv/bin/python

- Arguments: ["-m", "filesystem_rag_mcp.server"]

#### Zed Editor (~/.config/zed/settings.json)

Add an entry under context servers configuring the command and argument array using standard input/output transport.

### 6. Project Documentation Suite

For detailed specifications, operational guidelines, and phased execution plans, refer to the companion documents:

- High-Level Project Overview: Architectural breakdown, dual-mode operation, and technology stack.

- AGENTS.md: Developer guidelines, architectural invariants, TDD protocols, latency budgets, and subagent delegation rules.

- INSTRUCTIONS.md: Step-by-step implementation guide, client setups, operational CLI commands, and disaster recovery runbooks.

- Master Phased Implementation Plan: Consolidated roadmap bridging codebase reality with operational milestones.

- Phase 1: Workload Profiles & Domain Specialization Plan: Profile contracts and Vim artifact filtering.

- Phase 2: Hardware-Conscious Inference Plan: Ryzen 5 5600XT thread pinning and 3-stage retrieval funnel.

- Phase 3: Service Daemonization Plan: Systemd user units and multi-process SQLite WAL governance.

- Phase 4: Retrieval Quality Benchmarking Plan: 25-query ground-truth benchmark and MRR@5/Hit@1 gates.

- Phase 5: Automated Storage Maintenance Plan: Compaction primitives and weekly systemd timers.

### 7. License & Status

Released under the MIT License. Developed for local, privacy-first personal knowledge management and codebase intelligence on Arch Linux.
