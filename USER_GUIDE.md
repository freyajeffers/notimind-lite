# NotiMind Lite User Guide

Welcome to **NotiMind Lite**, your personal "notification insurance" policy. NotiMind Lite ensures that no important information ever truly disappears by intercepting, archiving, and making your system notifications searchable.

---

## 🚀 Getting Started

### How It Works
NotiMind Lite runs in the background, listening for system notifications. When a notification arrives, the app captures the content, the app that sent it, and the timestamp, storing it in a secure local database. Even if you dismiss a notification from your system tray, it remains available in NotiMind Lite for future reference.

### Basic Navigation
- **Active Notifications**: A real-time view of notifications currently present in your system tray.
- **Log History**: A deep archive of every notification captured since installation. This is where you can find dismissed alerts.
- **Actionable Chips**: NotiMind Lite automatically detects 2FA codes, tracking links, and locations, providing one-tap buttons (chips) to copy codes or open maps.

---

## 🔍 Mastering Hybrid Search

Finding a specific notification among thousands is easy thanks to our **Hybrid Search** engine. Unlike standard search, NotiMind Lite uses two different methods simultaneously to find what you need:

1. **Keyword Search (Full-Text Search)**: If you search for *"Bank Transfer"*, the app looks for those exact words using high-speed indexing.
2. **Semantic Search (Vector Embeddings)**: If you search for *"Money"*, the app understands that *"Bank Transfer"* or *"Payment Received"* are conceptually related, even if the word "money" isn't used.

**Pro Tip**: Use the **Log History** tab to leverage these search capabilities. The results are automatically merged and ranked using **Reciprocal Rank Fusion (RRF)**, which combines both keyword and semantic matches to ensure the most relevant results always appear at the top.

---

## ⏰ The Snooze System

Sometimes a notification is important, but you can't deal with it right now. Rather than leaving it to clutter your tray or dismissing it and forgetting, use the **Snooze** feature.

- **Setting a Snooze**: Select a notification and set a snooze timer (e.g., 30 minutes, 2 hours).
- **How it Behaves**: NotiMind Lite schedules a high-priority reminder. Even if your phone is in **Doze mode** (power saving), the app uses a specialized scheduler to ensure the reminder fires exactly when requested.
- **Reminder**: When the timer expires, you'll receive a fresh notification reminding you to attend to the original archived item.

---

## 💾 Backups & Recovery

Your data is stored locally for privacy, but we provide professional tools to ensure you never lose your history.

### Creating a Backup
1. Tap the **Quick-Action Menu** (FAB).
2. Select **Backup**.
3. The app creates an encrypted snapshot of your database.
4. **Notarization**: To ensure the backup is authentic and hasn't been tampered with, NotiMind Lite sends a cryptographic hash of the file to a **Remote Notary Server**. The server signs this hash, providing a "digital seal" of authenticity.

### Restoring a Backup
1. Go to settings or use the backup utility.
2. Select your backup file.
3. The app verifies the Notary signature. If the signature is valid, the encrypted database is decrypted and merged back into your current log.

---

## 🔐 Privacy & Security
- **Local First**: Your notifications are stored on your device.
- **Zero-Knowledge**: We use AES-256-GCM encryption for backups.
- **Integrity Verified**: Every backup is verified via Google Play Integrity to ensure it was created by a legitimate version of NotiMind Lite on a secure device.
