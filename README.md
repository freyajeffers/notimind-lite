# NotiMind Lite

NotiMind Lite is a privacy-focused Android notification insurance system. It captures, logs, and recovers system notifications using a sophisticated hybrid search architecture (Semantic + Keyword) and secure cloud synchronization, ensuring you never lose a critical alert.

---

## 🚀 Key Features

### 🔍 Advanced Discovery (The Intelligence Layer)
- **Hybrid Semantic Search**: Combines traditional FTS4 keyword matching with vector embeddings for conceptual discovery (e.g., searching for "travel" finds "flight" or "hotel" notifications).
- **RRF (Reciprocal Rank Fusion)**: A sophisticated merging algorithm that balances keyword precision and semantic relevance to provide the most accurate search results.
- **Actionable Entity Extraction**: Automatically identifies and extracts actionable data from notifications to streamline recovery.
- **Local Vector Processing**: Privacy-first embeddings processed on-device to maintain data sovereignty.

### ☁️ Secure Synchronization & Backup
- **Bidirectional Cloud Sync**: Real-time synchronization with Firestore, allowing notification recovery across device migrations.
- **Encrypted Backups**: End-to-end encrypted local and cloud backups using a user-managed backup key.
- **Google Sign-In Integration**: Secure, seamless authentication via Firebase Auth.
- **Backup Notary Service**: Ensures backup integrity and versioning through a dedicated notary client.

### 🛡️ Reliability & Persistence
- **Direct Boot Awareness**: Critical services (`NotificationLoggerService`) and receivers are marked `directBootAware`, allowing notification capture and recovery immediately after reboot, even before user unlock.
- **Boot Recovery Guard**: Automatically restores active notification state upon device restart.
- **Snooze & Reminders**: Integrated `SnoozeReminderScheduler` to bring dismissed but important notifications back to the user's attention.
- **Intelligent Filtering**: 
  - **Deduplication**: Content-based hashing to prevent log clutter.
  - **Blacklist**: Automatic filtering of system-level noise.
  - **Retention**: Configurable cleanup of stale historical data.

### 🎨 Modern UI/UX
- **Material 3 Dynamic Theming**: A polished, dark-themed interface built with Jetpack Compose.
- **Contextual Detail Panels**: Deep-dive views for notification metadata and actionable chips.
- **Adaptive Navigation**: Seamless flow between Active Notifications, Log History, and System Settings.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Persistence**: 
  - **Room SQLite**: Core storage with FTS4 for keyword search.
  - **Vector Storage**: Local embedding persistence for semantic retrieval.
- **Cloud**: Firebase Auth & Firestore
- **Background Work**: `WorkManager` (Sync), `NotificationListenerService` (Capture)
- **AI/ML**: On-device Vector Embeddings & Reciprocal Rank Fusion (RRF)
- **Dependency Injection/Processing**: KSP (Kotlin Symbol Processing)

---

## 📂 Project Structure

```
NotiMind-Lite/
├── app/
│   ├── src/main/java/com/jeffers/notimindlite/
│   │   ├── data/
│   │   │   ├── auth/           # Firebase Auth & Session Management
│   │   │   ├── local/          # Room DB, DAOs, FTS & Vector Entities
│   │   │   ├── sync/           # Firestore Sync & WorkManager
│   │   │   └── maps/           # Domain-specific entity detection
│   │   ├── service/            # NotificationListener & Restoration logic
│   │   ├── receiver/            # Boot, Unlock, and Snooze Receivers
│   │   ├── ui/
│   │   │   ├── components/     # Reusable Compose widgets (Chips, Panels)
│   │   │   ├── screens/        # Feature screens (Log, Active, Settings)
│   │   │   └── theme/          # M3 Design System
│   │   └── util/               # Hybrid Search, Vector Utils, Backup Logic
│   └── google-services.json     # Firebase Configuration
├── build.gradle.kts            # Project-level build config
└── settings.gradle.kts         # Module management
```

---

## ⚙️ Building and Installation

### Requirements
- Android SDK (API 33+)
- JDK 17+
- Google Services Account (for Firebase features)

### Build & Test
```bash
# Build Debug APK
./gradlew assembleDebug

# Run Unit & Robolectric Tests
./gradlew testDebugUnitTest

# Install to Device
./gradlew installDebug
```

---

## 📜 License
Internal / Open Source (NotiMind Project).
