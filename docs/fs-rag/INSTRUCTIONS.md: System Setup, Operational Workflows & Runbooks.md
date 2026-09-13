# INSTRUCTIONS.md: System Setup, Operational Workflows & Runbooks

## Local Filesystem RAG MCP Server

### 1. Prerequisites & Environment Setup

This section outlines the setup procedure for Arch Linux workstations running Python 3.12+.

- System Packages: Ensure standard build tools and SQLite libraries are available via Pacman (e.g., python, sqlite, git, base-devel).

- Package Manager: The repository uses uv for fast, deterministic dependency resolution and virtual environment management.

- Virtual environment target directory: ~/.local/share/mcp-rag/venv

- Synchronize dependencies: Run uv sync to install pinned packages from uv.lock.

- Directory Layout: Verify or create the default personal notes vault directory:

- mkdir -p ~/.local/share/notes

### 2. Phased Implementation Step-by-Step Guide

#### Step 1: Implementing Workload Profiles (Phase 1)

- Open src/filesystem_rag_mcp/config.py and add profile data structures supporting PersonalNotesProfile and GeneralCodebaseProfile.

- Configure default path resolution targeting ~/.local/share/notes for the notes profile.

- Update watcher.py and indexing.py with artifact exclusion patterns (.*.swp, .*.swo, .*.swx, .*.un~, *~, *.tmp).

- Verify that Markdown AST parsing in semantic_chunker.py extracts and retains heading breadcrumbs in chunk metadata.

- Run diagnostics via python -m filesystem_rag_mcp.cli doctor to confirm profile validation.

#### Step 2: Applying Hardware Thread Pinning & Model Warm-Up (Phase 2)

- Configure ONNX Runtime session options in vector.py and search.py to set intra_op_num_threads = 6 and inter_op_num_threads = 1 when running on the host Ryzen 5 5600XT.

- Implement an eager warm-up routine during server initialization executing synthetic inference passes on both bi-encoder and cross-encoder sessions.

- Calibrate the retrieval funnel in search.py: retrieve top 50 lexical and top 50 vector candidates, merge via RRF ($k=60$), cut to top 20 for cross-encoder reranking, and project top 5.

- Verify that repeated queries are intercepted by query_cache.py.

#### Step 3: Service Daemonization & Systemd Integration (Phase 3)

- Generate the systemd user service unit file at: ~/.config/systemd/user/mcp-rag-watcher.service.

- Set ExecStart to point to ~/.local/share/mcp-rag/venv/bin/python -m filesystem_rag_mcp.watcher.

- Configure Restart = on-failure and RestartSec = 5s.

- Enable and start the service:

- systemctl --user daemon-reload

- systemctl --user enable --now mcp-rag-watcher.service

- Enable user lingering so the daemon runs persistently across logins:

- loginctl enable-linger $USER

- Inspect logs using: journalctl --user -u mcp-rag-watcher.service -f.

#### Step 4: Executing Benchmark Evaluation & CI Gates (Phase 4)

- Populate the 25-query ground-truth dataset in tests/data/evaluation_dataset.json.

- Run the automated evaluation harness:

- python -m filesystem_rag_mcp.cli benchmark

- Verify that Mode 4 (Hybrid + Cross-Encoder) achieves superior MRR@5 compared to BM25 and vector alone.

- Confirm that end-to-end latency meets the sub-75ms average target.

- Ensure GitHub Actions workflow (.github/workflows/ci.yml) passes cleanly.

#### Step 5: Configuring Storage Maintenance & Timers (Phase 5)

- Implement the database optimization command executing PRAGMA incremental_vacuum; and FTS5 index segment optimization.

- Create the systemd user timer unit at: ~/.config/systemd/user/mcp-rag-maintenance.timer scheduled to run weekly.

- Enable the maintenance timer:

- systemctl --user daemon-reload

- systemctl --user enable --now mcp-rag-maintenance.timer

### 3. Client Integration Configurations

To connect MCP clients over stdio, use the following configuration profiles:

#### Claude Desktop Configuration (~/.config/Claude/claude_desktop_config.json)

Configure the server executable to point directly to the virtual environment Python interpreter:

- Command: /home/user/.local/share/mcp-rag/venv/bin/python

- Arguments: ["-m", "filesystem_rag_mcp.server"]

- Environment Variables: Specify active profile and database path as needed.

#### Zed Editor Configuration (~/.config/zed/settings.json)

Add an entry under context servers pointing to the Python module command with stdio transport.

### 4. Operational Runbooks & Troubleshooting

#### Resolving Database Locks

- Symptom: Operations fail with "database is locked" or busy timeout exhaustion.

- Resolution: Verify that all processes are using WAL mode (PRAGMA journal_mode; should return wal). Check for stale processes holding locks using fuser or lsof on the SQLite database file and terminate orphaned instances cleanly.

#### Inotify Watch Limit Exhaustion

- Symptom: Watcher daemon fails with error indicating inotify watch limits reached.

- Resolution: Increase system inotify watch limits in /etc/sysctl.d/99-sysctl.conf by adding fs.inotify.max_user_watches = 524288 and reloading with sudo sysctl -p.

#### Full Index Rebuild Procedure

- Stop the background watcher service: systemctl --user stop mcp-rag-watcher.service.

- Execute a complete rebuild: python -m filesystem_rag_mcp.cli reindex --reindex-all.

- Restart the service: systemctl --user start mcp-rag-watcher.service.

- Verify index stats: python -m filesystem_rag_mcp.cli stats.
