# NotiMind Lite: Architectural Design Decisions

This document captures the engineering rationale behind the core architectural choices of NotiMind Lite. It serves as a record of trade-offs and justifies the selection of specific algorithms and patterns over common alternatives.

---

## 1. Hybrid Search: Reciprocal Rank Fusion (RRF) vs. Weighted Sum

**Decision**: Use **Reciprocal Rank Fusion (RRF)** to combine Full-Text Search (FTS) and Vector results.

**Rationale**:
The primary challenge in hybrid search is the "score incompatibility" problem. 
- **FTS (SQLite FTS4)** returns scores based on term frequency and document length (BM25-like), where higher numbers indicate better matches, and the range is unbounded.
- **Vector Search** returns Cosine Similarity, which is strictly bounded between $-1.0$ and $1.0$.

A **weighted sum** (e.g., $0.7 \cdot \text{FTS} + 0.3 \cdot \text{Vector}$) would require complex normalization of the FTS scores to prevent the keyword pass from completely overwhelming the semantic pass.

**RRF** ignores the raw score and focuses exclusively on the **rank**. By calculating the score as $\sum \frac{1}{k + \text{rank}}$, RRF provides a stable, scale-invariant merge. It ensures that a document ranked #1 in either list is strongly promoted, but a document that appears moderately high in both lists is ranked higher than one that is #1 in only one.

---

## 2. Encryption: AES-GCM vs. Other Modes

**Decision**: Use **AES-256-GCM** (Galois/Counter Mode) via the Tink library.

**Rationale**:
Standard encryption modes like AES-CBC provide **confidentiality** but not **integrity**. A malicious actor could modify the ciphertext, and the decryption process would produce garbage data without alerting the system.

**AES-GCM** provides **Authenticated Encryption with Associated Data (AEAD)**. It produces a 16-byte authentication tag alongside the ciphertext. During decryption, if a single bit of the ciphertext or IV is altered, the tag verification fails, and a `GeneralSecurityException` is thrown. This prevents "bit-flipping" attacks and ensures that backups are either perfectly intact or explicitly rejected.

---

## 3. Security: Remote Notary vs. Local Salt Delivery

**Decision**: Implement a **Remote Notary** for backup signing rather than delivering a salt/key to the device.

**Rationale**:
The goal is "Zero-Knowledge Signing." If the signing key or the secret salt were delivered to the app (even in an encrypted form), the key would eventually exist in the device's memory. On rooted devices or via memory dumping, an attacker could extract the key and sign forged backups offline.

By using a **Remote Notary**, the signing key never leaves the secure server. The app must prove its identity and integrity via the **Google Play Integrity API**. The server only signs the file hash if:
1. The APK signature is authentic.
2. The device is not rooted/compromised.
3. The request is fresh (verified via nonce).

This shifts the trust root from the app binary to a combination of hardware attestation and server-side policy.

---

## 4. Search Index: Local SQLite FTS4 vs. Remote Index

**Decision**: Use a **Local SQLite FTS4** index for primary searching.

**Trade-off Analysis**:

| Metric | Local FTS4 (Chosen) | Remote Search Index |
| :--- | :--- | :--- |
| **Latency** | Near-zero (local I/O). | High (network round-trip). |
| **Privacy** | Maximum. Data never leaves device. | Lower. Queries must be sent to server. |
| **Availability** | Works offline. | Requires internet connection. |
| **Capability** | Basic keyword/prefix matching. | Advanced NLP / Global Aggregation. |
| **Complexity** | Low (integrated in Room). | High (requires index sync/sharding). |

**Conclusion**: Given that NotiMind Lite is positioned as a "privacy-first" notification insurance tool, the latency and privacy costs of a remote index were unacceptable. Local FTS4 provides the necessary speed and security, while the `HybridSearchEngine` fills the semantic gap using local vector computations.
