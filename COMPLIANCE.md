# Privacy & Legal Compliance Framework: NotiMind Lite

## 1. Data Lifecycle Audit

### Capture Phase
- **Source**: `NotificationLoggerService` (Android `NotificationListenerService`).
- **Data Collected**: 
    - Notification Title, Content (Body text).
    - Application Package Name, App Label (Name).
    - Post Time, Category, Priority.
    - App Icons (cached locally as PNGs).
- **Purpose**: To enable semantic search and archival of system notifications.
- **Consent**: Requires explicit Android `BIND_NOTIFICATION_LISTENER_SERVICE` permission granted by the user in System Settings.

### Local Storage Phase
- **Mechanism**: Room DB (SQLite).
- **Location**: Device Protected (DE) and Credential Encrypted (CE) storage.
- **Data**: Full `NotificationEntity` records including FTS indices and vector embeddings.
- **Persistence**: Persistent until manually deleted or app uninstalled.

### Cloud Sync Phase
- **Mechanism**: `FirestoreSyncRepository` $\rightarrow$ Google Cloud Firestore.
- **Identity**: Firebase Auth (Google Sign-In).
- **Data Synced**:
    - `users/{uid}/notifications/{notificationKey}`
    - All core fields (title, content, app name, etc.) are synced to ensure cross-device availability.
- **Security**: Access is restricted via Firebase Security Rules to the authenticated user (`auth.uid == userId`).

### Analysis
- **Minimization**: The system captures minimal fields required for the core utility. 
- **Purpose Limitation**: Data is used solely for archival and search. No third-party sharing occurs.
- **Risk**: Cloud sync stores plaintext notification content in Firestore. While protected by Auth rules, this is a centralized point of failure. (Recommendation: Implement end-to-end encryption for cloud sync in future versions).

---

## 2. Compliance Verification (GDPR / CCPA)

| Requirement | Status | Implementation |
| :--- | :--- | :--- |
| **Right to be Informed** | ✅ | Covered by Privacy Policy. |
| **Purpose Limitation** | ✅ | Data only used for archival/search. |
| **Data Minimization** | ✅ | Only notification metadata and content captured. |
| **Right of Access** | ✅ | User has full access to their data via the app UI. |
| **Right to Erasure** | ⚠️ | **PENDING**: Logic for total cloud/local purge needs implementation. |
| **Data Portability** | ✅ | Supported via CSV Export (documented in FAB). |
| **Consent** | ✅ | OS-level permission for notification access. |
