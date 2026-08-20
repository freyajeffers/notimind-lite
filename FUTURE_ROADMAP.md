# Future Roadmap: NotiMind Lite Intelligence Layer

This document outlines the next-generation AI capabilities prototyped for the NotiMind Lite Intelligence Layer, detailing their implementation, performance expectations, and the hurdles associated with full production deployment.

---

## 1. On-Device Notification Summarization ('Daily Digests')

### Prototype Overview
The `NotificationSummarizer` prototype implements a semantic grouping mechanism that aggregates dismissed notifications into high-level domains. It identifies "Key" notifications based on urgency markers and generates a concise natural language overview of the user's daily notification activity.

**Core Logic:**
- **Semantic Aggregation**: Uses `DynamicClusterManager` to group notifications by domain (e.g., Finance, Social).
- **Importance Filtering**: Scans for high-priority tokens (e.g., "urgent", "action required").
- **Simulated LLM Bridge**: Provides a `summarizeText` interface designed to connect to on-device models like Gemini Nano (via Google AICore).

### Performance Benchmarks (Estimated)
| Metric | Prototype (Rule-based) | Target (On-Device LLM) |
| :--- | :--- | :--- |
| **Execution Time** | $\approx 15\text{ms}$ | $200\text{ms} - 1,500\text{ms}$ |
| **Memory Overhead** | $\approx 2\text{MB}$ | $50\text{MB} - 200\text{MB}$ (Model Weights) |
| **Battery Impact** | Negligible | Low (Scheduled background task) |

### Implementation Hurdles
- **Model Quantization**: Balancing the quality of summaries with the memory constraints of lower-end Android devices.
- **Context Window**: Managing the input size when a user has hundreds of dismissed notifications in a single day.
- **Cold Start Latency**: The time required to load the LLM into memory for the first digest of the day.

---

## 2. Hierarchical Semantic Clustering

### Prototype Overview
The `DynamicClusterManager` was enhanced to move from a flat map of categories to a `ClusterNode` tree structure. This allows for a "drill-down" user experience.

**Enhancements:**
- **Multi-level Hierarchy**: Domains (e.g., `Finance`) now contain sub-clusters (e.g., `Banking`, `Crypto`, `Investing`).
- **Recursive Keyword Matching**: Broad domain matches now automatically include all keywords from their children.
- **Dynamic Assignment**: Package names and app labels are now mapped to the most specific sub-cluster available.

### Performance Benchmarks (Actual/Observed)
- **Initialization Time**: $< 50\text{ms}$ for 200+ installed apps.
- **Query Latency**: $\mathcal{O}(D \times S)$ where $D$ is the number of domains and $S$ is the average number of sub-clusters. Result is typically $< 5\text{ms}$.

### Implementation Hurdles
- **Vocabulary Maintenance**: Scaling the `HIERARCHICAL_VOCABULARY` without creating overlapping definitions between sub-clusters.
- **OS Category Mapping**: Android's `ApplicationInfo.CATEGORY_*` is often too broad; requiring more sophisticated package-name heuristic analysis.

---

## 3. Intelligent Suggestion Engine

### Prototype Overview
The `IntelligentSuggestionEngine` predicts which dismissed notifications a user is likely to need based on their historical interaction patterns and the current time of day.

**Core Logic:**
- **Temporal Weighting**: Matches the current hour against historical search timestamps to identify time-specific needs (e.g., "Work" apps in the morning, "Streaming" apps in the evening).
- **Frequency Scoring**: Applies a weight $\frac{1}{rank} \times frequency$ to historical queries to rank suggestions.
- **Global Fallback**: Reverts to overall most-searched patterns if no temporal match is found.

### Performance Benchmarks (Estimated)
| Metric | Value |
| :--- | :--- |
| **Prediction Latency** | $\approx 10\text{ms}$ |
| **Storage Cost** | $\approx 100\text{KB}$ per 1,000 search events |
| **Precision (Target)** | $> 70\%$ top-3 hit rate for repeat searches |

### Implementation Hurdles
- **Privacy/Zero-Trust**: Ensuring search history is stored locally and encrypted, avoiding any leakage to the cloud.
- **Data Sparsity**: The engine requires a "warm-up" period of several days of user interaction before predictions become accurate.
- **Dynamic Shifts**: Handling changes in user behavior (e.g., a user changing jobs or moving to a new city) which renders old temporal patterns obsolete.

---

## Summary of Next Steps
1. **Integration**: Connect `NotificationSummarizer` to a real TFLite or AICore implementation.
2. **UI Implementation**: Create a "Daily Digest" view in the UI layer and a "Suggested for You" section in the Log History.
3. **Feedback Loop**: Implement a "Was this suggestion helpful?" mechanism to refine the `IntelligentSuggestionEngine` weights.
