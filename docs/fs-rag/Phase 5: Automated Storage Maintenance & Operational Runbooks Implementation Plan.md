# Phase 5: Automated Storage Maintenance & Operational Runbooks Implementation Plan

## Filesystem RAG MCP Server (Consolidated Roadmap)

### 1. Executive Summary & Architectural Scope

Phase 5 establishes automated database maintenance routines, scheduled systemd timers, and operational runbooks to ensure long-term database stability, compact storage footprints, and sustained low-latency query performance over years of continuous note updates and file modifications.

### 2. SQLite Database Maintenance Primitives

Frequent document revisions, chunk replacements, and deletions cause database fragmentation and unused page bloat over time. The storage layer must provide automated compaction routines:

- Incremental Vacuuming:

- Configure SQLite database creation flags with auto_vacuum = INCREMENTAL.

- Implement a maintenance routine executing PRAGMA incremental_vacuum; to reclaim unused database pages resulting from chunk deletions without locking the entire file.

- FTS5 Index Segment Optimization:

- Frequent inserts and updates fragment FTS5 inverted index b-trees into multiple distinct segments.

- Implement an optimization routine executing INSERT INTO chunks_fts(chunks_fts) VALUES('optimize'); to merge index segments into a single balanced structure, restoring peak keyword search speed.

- Structural Integrity Auditing:

- Implement diagnostic routines executing PRAGMA integrity_check; and PRAGMA foreign_key_check; to verify database health and identify corruption risks.

### 3. Administrative MCP Tool Exposure

Expose database maintenance primitives to external agents and administrative scripts through the FastMCP server interface:

- Maintenance Tool Primitive: Provide a tool (e.g., optimize_database) that triggers incremental vacuuming, FTS5 segment optimization, and integrity auditing.

- Status & Metrics Reporting: The tool returns a structured report indicating pre- and post-optimization database file sizes, reclaimed page counts, total indexed documents, and execution duration.

### 4. Automated Systemd User Maintenance Timer

To guarantee zero-maintenance upkeep on Arch Linux:

- Systemd Timer Unit: Create a user timer file at: ~/.config/systemd/user/mcp-rag-maintenance.timer.

- Configure the timer to trigger weekly during off-peak hours (e.g., OnCalendar = Sun *-*-* 03:00:00).

- Enable persistent scheduling (Persistent = true) so missed executions during system downtime trigger upon the next boot.

- Systemd Service Unit: Create the associated one-shot service file at: ~/.config/systemd/user/mcp-rag-maintenance.service executing the CLI maintenance command.

- Journald Audit Trail: Route maintenance execution logs to journald, providing full visibility into compaction history and database health metrics.

### 5. Operational Runbooks & Disaster Recovery Procedures

Document explicit operational runbooks for administrative tasks:

- Full Index Rebuild Runbook:

- Triggered when migrating embedding models, changing chunking boundaries, or resolving corrupt storage states.

- Steps: Stop the background watcher service, execute the CLI rebuild command with --reindex-all, verify database recreation, and restart the watcher daemon.

- Database Backup & Restoration Runbook:

- Utilize the SQLite online backup API or safe snapshot routines that respect WAL locks to generate point-in-time backups without interrupting active search queries.

- Model Cache Eviction & Updating Runbook:

- Procedures for refreshing Hugging Face / FastEmbed model caches and verifying ONNX model integrity.

### 6. Acceptance & Verification Criteria

- Executing database optimization commands cleanly reclaims storage space and compacts FTS5 index segments.

- Systemd maintenance timer installs and triggers on schedule, logging results to journald.

- The administrative MCP optimization tool returns accurate before/after storage metrics.

- Full index rebuild procedures execute deterministically and restore all search capabilities without data loss.

- Integrity check diagnostics confirm database health across extensive modification cycles.
