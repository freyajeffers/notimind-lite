# Phase 3: Service Daemonization & Systemd Lifecycle Management Implementation Plan

## Filesystem RAG MCP Server (Consolidated Roadmap)

### 1. Executive Summary & Architectural Scope

Phase 3 transitions the existing inotify filesystem watcher into an autonomous, self-healing background system daemon on Arch Linux. By creating structured systemd user service units, enabling persistent user lingering, directing telemetry to journald, and managing inter-process concurrency over the shared SQLite database, this phase provides a completely hands-off operational lifecycle.

### 2. Systemd User Service Architecture

The background watcher runs under the systemd user instance, eliminating root privilege requirements while providing robust process supervision:

- Unit File Location: Establish the service definition at: ~/.config/systemd/user/mcp-rag-watcher.service.

- Unit Section Specifications:

- Unit Header: Define service description, documentation links, and dependency ordering requiring default user targets (Wants = default.target, After = default.target).

- Service Execution Profile: Specify the exact absolute path to the Python executable within the dedicated virtual environment (~/.local/share/mcp-rag/venv/bin/python) running the watcher entry point.

- Environment & Configuration: Inject environment variables for the active workload profile, notes directory path, database location, and unbuffered Python output (PYTHONUNBUFFERED = 1).

- Restart Policy: Configure automatic recovery with Restart = on-failure and a restart delay of RestartSec = 5s to ensure resilience against transient failures while avoiding crash loops.

- Security & Sandboxing Directives: Apply standard systemd security directives where appropriate, including private temporary file spaces and memory utilization limits.

- Installation Target: Bind the service to WantedBy = default.target to ensure automatic startup upon user login.

- Persistent User Lingering: Document and configure user account lingering (loginctl enable-linger) so that the background service continues executing even when no interactive user session or desktop environment is active.

### 3. Structured Telemetry & Journald Integration

Service execution logs must integrate cleanly into systemd logging facilities:

- Stream Routing: Direct standard output and standard error streams directly to journal.

- Log Message Standardization: Format log records with timestamps, log severity levels (INFO, WARNING, ERROR), module identifiers, and event descriptions (e.g., file detected, hash matched, delta ingested).

- Log Inspection Runbooks: Provide standardized commands for inspecting live service logs, such as journalctl --user -u mcp-rag-watcher.service -f.

### 4. CLI Service Lifecycle Management

To provide a seamless developer experience, extend the existing command-line interface (cli.py) with service control subcommands:

- mcp-rag service install: Generates and links the systemd user unit file, reloads the systemd daemon, and enables the service.

- mcp-rag service start / stop / restart: Dispatches operational commands directly to systemctl --user.

- mcp-rag service status: Queries and displays unit active state, memory utilization, and recent journal entries.

### 5. Inter-Process Concurrency Governance

The architecture involves two distinct processes interacting with the same SQLite database: the background watcher daemon (writer) and the on-demand FastMCP server (reader/writer invoked by MCP clients over stdio):

- Write-Ahead Logging (WAL): Ensure all database connections across both processes enforce PRAGMA journal_mode = WAL;, allowing concurrent read transactions without blocking on active writes.

- Busy Timeout Protection: Enforce PRAGMA busy_timeout = 5000; on all database connections to handle transient write locks gracefully through automatic retries rather than throwing lock errors.

- Immediate Write Transactions: The watcher daemon must acquire write locks using immediate transaction semantics during delta indexing batches, ensuring that multi-table updates complete atomically.

- Graceful Signal Handling: Both processes must capture SIGINT and SIGTERM signals to complete in-flight transactions and flush WAL files before terminating.

### 6. Acceptance & Verification Criteria

- The systemd user unit installs cleanly and starts without configuration or permission errors.

- Killing the background watcher process triggers an automatic restart within 5 seconds.

- Logs are captured in journald with readable timestamps and severity levels.

- MCP clients successfully execute search queries over stdio while the background watcher actively writes index updates to the shared database.

- Enabling user lingering allows the daemon to run persistently across terminal and session logouts.
