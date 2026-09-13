# Phase 2: Hardware-Conscious Inference & Adaptive Thread Pinning Implementation Plan

## Filesystem RAG MCP Server (Consolidated Roadmap)

### 1. Executive Summary & Architectural Scope

Phase 2 configures and hardens the machine learning inference layer to maximize embedding and reranking performance on the host hardware—specifically tailored for the AMD Ryzen 5 5600XT processor (6 physical Zen 3 cores, 12 threads)—while maintaining graceful adaptability for other CPU architectures.

By isolating thread pools, eliminating execution provider contention, establishing a structured three-stage retrieval funnel, and integrating query caching, this phase ensures sustained sub-75ms search response times without causing workstation micro-stutters or background task throttling.

### 2. CPU Topology & Execution Provider Optimization

Running multiple transformer models (bi-encoder and cross-encoder) in the same process requires explicit execution provider tuning:

- Target Hardware Configuration (Host Profile):

- AMD Ryzen 5 5600XT possesses 6 physical cores and 12 hardware threads with a 32 MB unified L3 cache.

- Set ONNX Runtime execution provider options to intra_op_num_threads = 6, matching the physical core count to maximize vector math efficiency while avoiding SMT hyperthreading penalties.

- Set inter_op_num_threads = 1 to prevent independent operators from contending over execution units.

- Enable full graph optimization (ORT_ENABLE_ALL) during session creation.

- Adaptive Hardware Fallback:

- On foreign or non-host systems, inspect available physical cores dynamically.

- Cap intra-op thread counts at the physical core threshold to prevent scheduler thrashing on low-core or high-core machines.

- Session Isolation:

- Maintain separate, dedicated ONNX Runtime inference sessions for the bi-encoder embedding model (bge-small-en-v1.5) and the cross-encoder reranking model (bge-reranker-base).

- Ensure that embedding passes during background document indexing do not block or destabilize interactive cross-encoder sessions servicing foreground search queries.

### 3. Startup Graph Compilation & Model Warm-Up

Transformer models exhibit high latency during initial inference passes due to dynamic graph compilation and memory allocation:

- Eager Initialization: Pre-load model weights and instantiate execution provider sessions during server startup rather than deferring to the first user query.

- Synthetic Warm-Up Pass: Execute a synthetic embedding pass and reranking inference with dummy tensor inputs immediately following session creation.

- Cache Priming: Populate CPU cache hierarchies and verify memory allocation pools during warm-up to ensure that subsequent user queries experience zero cold-start latency.

### 4. Three-Stage Retrieval Funnel Calibration

The hybrid retrieval pipeline must balance comprehensive recall against computation budgets:

- Stage 1: Broad Recall Generation:

- Execute a lexical BM25 search over SQLite FTS5 indices to retrieve the top 50 keyword matches.

- Compute query embeddings via the bi-encoder and calculate cosine similarity across chunk vectors to retrieve the top 50 semantic matches.

- Combine the two candidate sets using Reciprocal Rank Fusion (RRF) with a ranking constant of k = 60, producing a unified ranked pool of candidate chunks.

- Stage 2: Cross-Encoder Reranking Cut:

- Extract the top 20 candidates from the RRF candidate pool.

- Formulate query-chunk pairs and process them through the cross-encoder reranker in a single batched inference call.

- Transform raw model output logits into calibrated probability scores using a sigmoid activation function.

- Stage 3: Output Projection:

- Sort reranked candidates by their calibrated sigmoid relevance scores.

- Select the top 5 highest-scoring chunks for delivery to the MCP client.

- Attach relevance scores, chunk identifiers, and heading breadcrumb metadata to the response payload.

### 5. Query Cache Integration & Batch Throttling

- Embedding & Rerank Caching: Leverage the existing query cache module (query_cache.py) to store query embeddings and rerank results for repeated or syntactically identical queries, returning results in sub-millisecond time.

- Batch Size Limits for Background Ingestion: Restrict embedding generation batch sizes during background document ingestion to manageable blocks (e.g., 16–32 chunks) to prevent memory ballooning and preserve CPU headroom for desktop tasks.

### 6. Acceptance & Verification Criteria

- Inference sessions initialize with explicit thread limits (6 intra-op, 1 inter-op on host).

- Warm-up routines execute successfully, eliminating cold-start spikes on initial queries.

- Stage 1 generates exactly 50 lexical and 50 semantic candidates, properly merged via RRF.

- Stage 2 evaluates exactly 20 candidates in a single cross-encoder inference pass.

- Interactive queries execute concurrently with background indexing without triggering UI lag or system lockups.
