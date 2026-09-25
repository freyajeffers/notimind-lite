# Privacy Policy for NotiMind Lite

**Last Updated**: August 18, 2026

NotiMind Lite is committed to a "Privacy-First" architecture. We believe your notification history is deeply personal and should remain under your sole control.

## 1. Data Collection
NotiMind Lite captures system notifications from your device. The data collected includes:
- **Notification Content**: Title and body text of notifications.
- **App Metadata**: Package name, app label, and app icons.
- **Temporal Data**: Timestamp of when the notification was posted.
- **Technical Metadata**: Notification category and priority.

**We do not collect**:
- Personal identifiers (unless provided via Google Sign-In).
- Location data.
- Contact lists or call logs.
- Precise device telemetry.

## 2. How We Use Your Data
Your data is used exclusively to provide the following features:
- **Local Archival**: Saving notifications so they are no longer ephemeral.
- **Semantic Search**: Allowing you to find old notifications using natural language.
- **Cloud Synchronization**: (Optional) Syncing your archive across devices via Google Cloud Firestore.

## 3. Data Storage & Security
### Local Storage
Data is stored on your device using Room with SQLCipher open helpers for the credential-encrypted and device-encrypted database instances. Per-database passphrases are wrapped with Android Keystore keys. Existing plaintext database migration is not yet implemented; users upgrading from a pre-SQLCipher build require a migration release before this guarantee applies to legacy files.

### Cloud Synchronization
If you enable Cloud Sync:
- Data is stored in Google Cloud Firestore.
- **Strict Access Control**: Data is siloed by your unique Firebase UID. Security rules ensure that only you can read or write your own data.
- **No Provider Access**: NotiMind Lite does not maintain a separate backend that monitors your notification content.

## 4. Third-Party Sharing
**We do not sell, trade, or share your notification data with any third parties.** The only third-party services utilized are:
- **Google Firebase**: For authentication and cloud storage.
- **Google Play Integrity**: To verify device health during backup notarization.

## 5. Your Rights (GDPR/CCPA)
Regardless of your location, we provide the following controls:
- **Right to Access**: All captured data is viewable within the app.
- **Right to Portability**: You may export your notification history as a CSV file.
- **Local clearing**: You can clear the local notification log from Settings. This does not delete the Firestore copy.
- **Cloud deletion**: Application-level cloud purge is currently disabled by policy; a GDPR/account-erasure workflow remains pending and must be implemented with matching Firestore Rules changes.

## 6. Contact
For privacy-related inquiries, please contact the development team via the project repository.
