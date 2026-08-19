# Remote Notary Server API Specification

This document defines the formal API contract for the NotiMind Lite Remote Notary Server. The Notary Server provides a "Zero-Trust" attestation service that signs backup hashes only after verifying the identity and integrity of the requesting client.

---

## 🔐 Attestation Handshake Flow

The notarization process ensures that backups are created by a legitimate, untampered version of the NotiMind Lite application on a secure Android device.

1. **Hash Generation**: The client generates a $\text{SHA-256}$ hash of the encrypted backup file.
2. **Hardware Attestation**: The client requests a cryptographically signed integrity token from the **Google Play Integrity API**.
3. **Submission**: The client submits the file hash, the integrity token, and a server-provided nonce to the `/v1/notarize` endpoint.
4. **Server-Side Verification**:
   - **Token Validation**: The server decrypts and verifies the Google Play Integrity JWT.
   - **Binary Verification**: The server checks that the APK hash in the token matches the official release hash.
   - **Environment Check**: The server verifies that `deviceIntegrity` is `MEETS_DEVICE_INTEGRITY`.
5. **Signing**: Upon successful verification, the server computes a signature:
   $$\text{Signature} = \text{HMAC-SHA256}(\text{InternalSalt}, \text{FileHash})$$
6. **Response**: The server returns the signature, which the client stores as the "digital seal" for that backup.

---

## 🚀 API Endpoints

### Notarize Backup
Signs a backup file hash after successful device and binary attestation.

- **Endpoint**: `POST /v1/notarize`
- **Content-Type**: `application/json`

#### Request Schema
| Field | Type | Description |
| :--- | :--- | :--- |
| `file_hash` | `string` | The SHA-256 hex string of the encrypted backup file. |
| `integrity_token` | `string` | The JWT token provided by the Google Play Integrity API. |
| `nonce` | `string` | A unique, server-generated nonce to prevent replay attacks. |
| `timestamp` | `string` | ISO-8601 formatted timestamp of the request. |

**Example Request:**
```json
{
  "file_hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "integrity_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
  "nonce": "a7f8b9c0d1e2f3g4h5i6j7k8l9m0n1o2",
  "timestamp": "2026-08-18T14:30:00Z"
}
```

#### Response Schema (`200 OK`)
| Field | Type | Description |
| :--- | :--- | :--- |
| `signature` | `string` | The HMAC-SHA256 hex string signature of the file hash. |
| `notary_id` | `string` | The identifier of the server node that performed the signing. |
| `expires_at` | `string` | ISO-8601 timestamp indicating when the signature's validity window expires. |

**Example Response:**
```json
{
  "signature": "7d2f3a1b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f",
  "notary_id": "notary-us-east-01",
  "expires_at": "2026-08-19T14:30:00Z"
}
```

---

## ❌ Error Codes

| HTTP Code | Error Code | Meaning | Resolution |
| :--- | :--- | :--- | :--- |
| `400` | `INVALID_REQUEST` | Missing required fields or malformed JSON. | Check request schema. |
| `401` | `ATTESTATION_FAILED` | Integrity token is invalid, expired, or forged. | Request a fresh token from Play Integrity API. |
| `403` | `UNTRUSTED_ENVIRONMENT` | Device is rooted or fails `deviceIntegrity` check. | Backup will be marked "Unverified". |
| `403` | `BINARY_MISMATCH` | APK hash does not match known official releases. | Update app to latest official version. |
| `429` | `TOO_MANY_REQUESTS` | Rate limit exceeded for the device/user. | Implement exponential backoff. |
| `500` | `INTERNAL_ERROR` | Server-side failure during signing. | Retry after delay. |
