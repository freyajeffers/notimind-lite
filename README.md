# NotiMind Lite

A privacy-focused, lightweight Android notification logger and boot recovery service designed for local notification persistence, filtering, and seamless reboot recovery without cloud or external AI/ML dependencies.

---

## Key Features

- **Local Notification Ingestion & Persistence**: Uses Android `NotificationListenerService` and Room DB to capture status bar notifications locally on device.
- **Smart Ingestion Filtering**:
  - **Deduplication Guard**: Prevents ingesting duplicate notifications by hashing package, title, and content.
  - **Blacklist Filter**: Filters system clutter (`android`, `com.android.systemui`, `com.android.shell`, `com.google.android.googlequicksearchbox`).
  - **30-Day Retention Filter**: Drops notifications older than 30 days.
  - **Spam & Clutter Filter**: Drops spam alerts, "X more notifications" summaries, and low-value system categories (`CATEGORY_SERVICE`, `CATEGORY_SYSTEM`).
- **Boot Recovery Guard**:
  - Automatically restores active notifications upon device reboot.
  - **Update Protection**: Records package update timestamp (`MY_PACKAGE_REPLACED`) to avoid triggering unwanted restores when the app or service restarts after an update.
- **Single-Card Enforcement**: Guaranteed max 1 card rendered per unique notification across screens.
- **Synchronized Dismissal**: Dismissing a card marks all matching instances as dismissed and cancels the notification from the system status bar.
- **Log History & Export**: Browse dismissed notifications with search and export capabilities (JSON/CSV).
- **Deletion Safeguard**: Programmatic and UI protections prevent accidental or mass deletion of logged notification history (`clearAll` disabled).

---

## Tech Stack & Architecture

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3 Dark Theme)
- **Navigation**: Navigation Compose
- **Database**: Room Persistence Library (`AppDatabase`, `NotificationDao`, `NotificationEntity`)
- **Concurrency**: Kotlin Coroutines & Flow
- **Testing**: JUnit 4, Robolectric, Room In-Memory DB

---

## Project Structure

```
NotiMind-Lite/
├── app/
│   ├── src/main/java/com/jeffers/notimindlite/
│   │   ├── data/
│   │   │   ├── local/
│   │   │   │   ├── AppDatabase.kt
│   │   │   │   ├── NotificationDao.kt
│   │   │   │   ├── NotificationEntity.kt
│   │   │   │   └── PreferenceManager.kt
│   │   │   └── export/
│   │   │       └── DatabaseExporter.kt
│   │   ├── receiver/
│   │   │   └── BootReceiver.kt
│   │   ├── service/
│   │   │   └── NotificationLoggerService.kt
│   │   ├── ui/
│   │   │   ├── MainActivity.kt
│   │   │   ├── Navigation.kt
│   │   │   ├── screens/
│   │   │   │   ├── ActiveNotificationsScreen.kt
│   │   │   │   └── LogHistoryScreen.kt
│   │   │   └── theme/
│   │   │       ├── Color.kt
│   │   │       └── Theme.kt
│   │   └── util/
│   │       └── NotificationLauncher.kt
│   └── src/test/java/
│       └── com/jeffers/notimindlite/ ... (Unit & Robolectric Tests)
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Database Schema (`notifications`)

| Column | Type | Description |
|---|---|---|
| `id` | `INTEGER` (PK) | Auto-generated primary key |
| `key` | `TEXT` (Indexed) | Deduplication & system notification key |
| `packageName` | `TEXT` | Package name of posting application |
| `appName` | `TEXT` | Formatted user-facing app name |
| `title` | `TEXT` | Notification title |
| `content` | `TEXT` | Notification body text |
| `postTime` | `INTEGER` (Indexed) | Timestamp when posted |
| `isDismissed` | `INTEGER` (Indexed) | `0` = Active, `1` = Dismissed |
| `isPersistent` | `INTEGER` | Ongoing status flag |
| `dismissReason` | `INTEGER` | Reason code for dismissal |
| `dismissTime` | `INTEGER` | Timestamp when dismissed |

---

## Building and Installation

### Requirements
- Android SDK (API 33+)
- JDK 17+
- ADB (for device deployment)

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Install to Device via ADB
Ensure your Android device is connected with USB Debugging enabled:
```bash
adb devices
./gradlew installDebug
```

---

## License

Internal / Open Source (NotiMind Project).
