# AGENTS.md: Developer & Coding Agent Guidelines

## Local Filesystem RAG MCP Server

### 1. Purpose & Agent Persona

This document establishes strict operational directives, architectural invariants, and development protocols for any autonomous coding agent, AI assistant, or software engineer contributing to the filesystem_rag_mcp codebase.

Agents operating in this repository must adopt the persona of a precision systems engineer: prioritize correctness over cleverness, maintain non-blocking concurrency, respect host hardware constraints, write comprehensive tests before landing code, and leave no architectural debt.

### 2. Core Architectural Invariants (Non-Negotiable Constraints)

- Zero Feature Degradation: The existing codebase contains mature capabilities for multi-format conversion, code symbol extraction, git operations, and grep tools. Enhancements for the personal notes profile MUST NOT break, remove, or degrade any existing general-purpose codebase capabilities.

- Strict Thread Pool Discipline: Never instantiate unmanaged ONNX Runtime sessions, PyTorch thread pools, or thread pools that default to logical thread counts. Intra-op threads must be bounded by physical core counts (max 6 on the host 5600XT architecture) and inter-op threads must remain 1.

- Non-Blocking SQLite Concurrency:

- All database connections must enforce Write-Ahead Logging (PRAGMA journal_mode = WAL;).

- All connections must configure a busy timeout of at least 5000 ms (PRAGMA busy_timeout = 5000;).

- Write operations must utilize immediate transaction semantics to prevent deadlocks.

- Never perform long-running CPU computation or file I/O inside open database transactions.

- Editor Artifact Exclusion: Any file matching editor temporary patterns (.*.swp, .*.swo, .*.swx, .*.un~, *~, *.tmp) must be rejected at the earliest possible filter boundary before entering ingestion queues.

- Strict Path Traversal & Sandboxing Security: All path operations must resolve symbolic links, normalize paths, and verify that target paths remain strictly within authorized profile roots. Direct un-sanitized path operations are prohibited.

- Stdio Purity for MCP Transport: The FastMCP server communicates with client applications via standard input and standard output (stdio) using JSON-RPC frames. Never emit debug print statements, unformatted logs, or progress bars to stdout. All logging must route strictly to stderr or systemd journald.

### 3. Test-Driven Development (TDD) Protocol

Every modification, bug fix, or feature addition must adhere to a strict verification protocol:

- Define Unit & Integration Tests First: Before implementing changes, write failing test cases in the tests/ directory that capture expected behavior and edge cases.

- Mock Expensive Subsystems: In unit tests, mock ONNX Runtime inference sessions and heavy model weights to ensure test execution remains instantaneous. Use integration tests with synthetic sample files to verify end-to-end pipelines.

- Validate Non-Blocking Concurrency: Concurrency features must be tested with simulated simultaneous read and write operations to guarantee that SQLite lock errors do not occur under load.

- Run Pre-Flight Regression Gates: Execute the full test suite and the 25-query benchmark runner before submitting or committing any change. Pull requests that lower MRR@5 or increase p95 latency beyond 100 ms must be rejected.

### 4. Inter-Module Contracts & Interface Rules

- Strict Typing: All public module functions, method parameters, and return types must be fully typed using Python type hints, conforming to py.typed standards.

- Data Class & Pydantic Boundaries: Use explicit typed data structures (such as Pydantic models or standard dataclasses) for all data moving across module boundaries (e.g., chunk payloads, retrieval candidates, document metadata).

- Decoupled Dependencies:

- The storage module must not import FastMCP or CLI components.

- The chunker module must not import SQLite or database connection pools.

- The retriever module must interact with storage strictly through abstract repository interfaces.

### 5. Performance SLAs & Latency Budgets

All code changes must operate within the following component latency budgets:

- FTS5 Lexical Search: < 5 ms (for 50 candidate chunks).

- FastEmbed Vector Cosine Search: < 15 ms (for 50 candidate chunks).

- Cross-Encoder Reranker: < 45 ms (for 20 candidate pairs on CPU).

- RRF Fusion & Response Formatting: < 10 ms.

- Total End-to-End Query Round-Trip: < 75 ms (with 95th percentile < 100 ms).

### 6. Subagent Delegation Patterns

When executing complex tasks, autonomous agents should delegate discrete subtasks to child subagents to prevent context bloat:

- Benchmark Suite Execution: Delegate the execution and scoring of the 25-query benchmark dataset across the four retrieval modes to an isolated subagent.

- Corpus Verification & Ingestion Auditing: Delegate test corpus generation, multi-format file conversion verification, and mock document creation to dedicated subagents.

- Log Analysis & Concurrency Profiling: Delegate parsing of journald logs and SQLite transaction timing traces to a subagent during performance debugging.

### 7. Error Recovery & Graceful Degradation

- Model Failure Fallback: If cross-encoder reranking fails or times out, the retrieval pipeline must fall back to returning the Stage 1 RRF candidates rather than failing the user query.

- Transient Lock Recovery: If an immediate write transaction encounters a lock timeout after 5000 ms, the ingestion worker must requeue the event with an exponential backoff delay instead of dropping the document update.

- Signal Handling: Both watcher and server processes must trap SIGINT and SIGTERM signals to complete in-flight transactions and flush WAL files cleanly.
