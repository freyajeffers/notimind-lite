# Phase 4: Retrieval Quality Benchmarking & Regression Gates Implementation Plan

## Filesystem RAG MCP Server (Consolidated Roadmap)

### 1. Executive Summary & Architectural Scope

Phase 4 establishes an automated evaluation harness and quantitative regression gates to ensure search quality and verify that structural, prompt, or model changes do not degrade retrieval accuracy. By curating a ground-truth dataset and tracking Mean Reciprocal Rank (MRR@5) and Hit@1 across diverse query types, this phase introduces rigorous verification into both local workflows and automated CI pipelines.

### 2. Curated Ground-Truth Benchmark Dataset

To evaluate retrieval effectiveness objectively, construct a version-controlled benchmark dataset:

- Dataset Composition: Curate at least 25 representative queries mapped to verified ground-truth document IDs and chunk identifiers.

- Query Diversity Matrix:

- Exact Keyword Queries (6 queries): Tests FTS5 BM25 token matching, exact terminology, and syntax lookups.

- Conceptual Semantic Queries (8 queries): Tests vector cosine similarity on abstract concepts where exact wording differs from the source text.

- Code Syntax & Symbol Queries (6 queries): Tests retrieval of function definitions, variable assignments, and programming constructs.

- Hierarchical Heading Queries (5 queries): Tests breadcrumb matching where the target answer is nested beneath specific multi-level Markdown headings.

- Dataset Storage: Maintain the evaluation dataset in a structured format (e.g., JSON or YAML) within the repository under a dedicated evaluation directory.

### 3. Automated Evaluation Runner Architecture

The evaluation runner executes the ground-truth benchmark across four distinct retrieval modes:

- Mode 1: Pure Lexical (FTS5 BM25): Evaluates retrieval performance using only keyword matching.

- Mode 2: Pure Dense Vector: Evaluates retrieval performance using only bi-encoder cosine similarity.

- Mode 3: Hybrid RRF: Evaluates the combined lexical and semantic candidate pool fused via Reciprocal Rank Fusion ($k=60$) without cross-encoder reranking.

- Mode 4: Two-Stage Hybrid + Cross-Encoder Reranking: Evaluates the full production pipeline, reranking the top 20 candidates and projecting the top 5.

### 4. Quantitative Metric Tracking & Quality Thresholds

For each evaluation mode, the runner computes and reports standardized information retrieval metrics:

- Mean Reciprocal Rank (MRR@5): Calculates the reciprocal of the rank at which the first relevant document chunk is retrieved (score of 1.0 for rank 1, 0.5 for rank 2, down to 0.2 for rank 5, and 0 for ranks > 5).

- Hit@1: The proportion of queries where the top-ranked retrieved chunk is a relevant ground-truth item.

- Quality Gate Criteria:

- The full hybrid + cross-encoder pipeline (Mode 4) must achieve an MRR@5 score superior to any single modality (Mode 1 or Mode 2 alone).

- Hit@1 must meet or exceed a baseline threshold of 0.80 on the curated technical query dataset.

### 5. Latency SLA Enforcement & Component Budgets

The evaluation suite must measure execution time across pipeline stages and enforce strict latency budgets:

- Component Latency Budgets:

- FTS5 Keyword Search (50 candidates): < 5 ms.

- FastEmbed Dense Vector Search (50 candidates): < 15 ms.

- Cross-Encoder Rerank (20 candidates): < 45 ms.

- RRF Fusion & Response Formatting: < 10 ms.

- Total Round-Trip Query Time: < 75 ms (with 95th percentile < 100 ms).

- Regression Failure Condition: If the 95th percentile latency exceeds 100 ms over 100 test iterations, the evaluation runner flags a performance regression.

### 6. CI/CD Integration & Developer Tooling

- Local Pre-Flight Command: Provide a CLI subcommand (e.g., mcp-rag benchmark) allowing developers to run the evaluation suite locally and view formatted metric tables.

- Continuous Integration Gate: Incorporate the benchmark suite into GitHub Actions (.github/workflows/ci.yml) as a required status check before merging changes.

### 7. Acceptance & Verification Criteria

- The benchmark runner executes all 25 queries across all four retrieval modes without runtime errors.

- Evaluation reports confirm that the hybrid + cross-encoder pipeline outperforms standalone lexical and vector baselines.

- Average round-trip query latency adheres to the sub-75ms budget.

- CI workflow executes the benchmark successfully and reports metrics on pull requests.
