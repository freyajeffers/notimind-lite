# Technical Specification: NotiMind Lite Backup Notary Server

## Overview
The Backup Notary Server is a trusted third-party service that provides cryptographic signatures for encrypted backup files. By offloading the signing process to the server, the application never possesses the master signing salt, making it impossible to extract the salt via decompilation or memory dumping.

The server acts as a **Digital Notary**: it verifies the app's identity via the Google Play Integrity API and signs the hash of the backup file.

---

## 1. The Notary Protocol

The app does not request a salt; instead, it requests a signature for a specific file fingerprint.

### Request Flow
1. **Local Hashing**: The app calculates the `SHA-256` hash of the encrypted backup file.
2. **Identity Attestation**: The app requests a Google Play Integrity Token.
3. **Signature Request**: The app sends the `Hash` + `Integrity Token` to the Notary Server.
4. **Server-Side Validation**:
    - Decrypts and verifies the Integrity Token with Google.
    - Ensures the app is genuine, untampered, and signed by the official developer.
    - Checks that the `packageName` is `com.jeffers.notimindlite`.
5. **Signing**: The server signs the `Hash` using the internal master signing salt (`HMAC-SHA256`).
6. **Response**: The server returns the `Signature`.

---

## 2. API Specification

### `POST /v1/internal/sign-hash`

**Request Headers**: `Content-Type: application/json`
**Request Body**:
```json
{
  "hash": "string (SHA-256 hex digest of the encrypted file)",
  "token": "string (Google Play Integrity Token)"
}
```

**Success Response (200 OK)**:
```json
{
  "signature": "SIG_a1b2c3d4e5f6...",
  "timestamp": "2026-08-17T10:00:00Z"
}
```

**Failure Response (403 Forbidden)**:
```json
{
  "error": "ATTESTATION_FAILED",
  "message": "App identity could not be verified. Backup authorization denied."
}
```

---

## 3. Cryptographic Logic (Server-Side)

The server implements the following deterministic signing function:

$$\text{Signature} = \text{HMAC-SHA256}(\text{InternalSalt}, \text{FileHash})$$

**Key Properties**:
- **InternalSalt**: A high-entropy secret stored only in the server's secure environment variables/KMS.
- **Determinism**: The same file hash always produces the same signature, allowing for future verification.
- **Non-Repudiation**: Only the Notary Server can produce this signature.

---

## 4. Security Analysis

| Threat | Mitigation |
| :--- | :--- |
| **Static Analysis** | The signing salt is completely absent from the app binary. |
| **Memory Dumping** | No secret keys are stored in RAM; only the final signature is received. |
| **API Spoofing** | Requests without a valid, current Play Integrity Token are rejected. |
| **Backup Tampering** | Any change to the encrypted file changes the hash, rendering the signature invalid. |
| **Key Leakage** | Even if a user's secret key is leaked, the attacker cannot "authorize" a fake backup without the server's signature. |

## 5. Implementation Notes
- **Rate Limiting**: The `/sign-hash` endpoint should be rate-limited per user/device to prevent brute-force attempts to find hash collisions.
- **Logging**: Log all attestation failures to identify potential modified APKs or botting attempts.
- **KMS**: Use a Hardware Security Module (HSM) or Cloud KMS (e.g., AWS KMS, Google Cloud KMS) to store the `InternalSalt`.
