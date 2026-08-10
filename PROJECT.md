# Project: NotiMind Lite

## Architecture
Standalone light Android app stripped of AI/ML, cloud sync, and Firebase.
Packages:
- `com.notimind.lite.data`: Room DB entities, DAOs, Database
- `com.notimind.lite.service`: NotificationLoggerService, BootReceiver
- `com.notimind.lite.ui`: Jetpack Compose Screens (Active, History, Settings), Navigation host, Theme
- `com.notimind.lite.ui.navigation`: Routes and NavGraph

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Core Architecture & Setup | Gradle setup, Kotlin, AndroidX, Compose, Room dependencies, Application class, AndroidManifest | None | IN_PROGRESS |
| 2 | Data Persistence Schema | Room DB (`AppDatabase`, `NotificationDao`, `NotificationEntity`) metadata: id, key, packageName, appName, title, content, postTime, isDismissed, isPersistent | M1 | PLANNED |
| 3 | Core Services & Boot Recovery | `NotificationLoggerService` (extending `NotificationListenerService`), `BootReceiver` (`ACTION_BOOT_COMPLETED`, restore active notifications, rebind service) | M2 | PLANNED |
| 4 | Multi-Screen Jetpack Compose UI | Navigation routes, Active Notifications Screen, Log History Screen (filter/search), Settings Screen (permission status link, clear log, boot toggle) | M3 | PLANNED |
| 5 | Final E2E Test Suite Pass & Coverage Hardening | 100% pass rate on E2E test suite (Tiers 1-4) + Tier 5 adversarial testing | M4, E2E-TESTS | PLANNED |

## E2E Testing Track
| Track | Description | Artifacts | Status |
|-------|-------------|-----------|--------|
| E2E-TESTS | Comprehensive opaque-box test suite (Tiers 1-4) based on ORIGINAL_REQUEST.md requirements | `TEST_INFRA.md`, `TEST_READY.md`, unit/integration test suites | IN_PROGRESS |

## Interface Contracts
### Data ↔ Services
- `NotificationDao.insert(entity: NotificationEntity)`
- `NotificationDao.getActiveNotifications(): Flow<List<NotificationEntity>>`
- `NotificationDao.getAllNotifications(): Flow<List<NotificationEntity>>`
- `NotificationDao.markDismissed(key: String)`
- `NotificationDao.clearAll()`

### Services ↔ OS
- `NotificationLoggerService` binds with `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`
- `BootReceiver` receives `android.intent.action.BOOT_COMPLETED`

### Data ↔ UI
- ViewModels observe DAOs / flow state and update Compose UI.

## Code Layout
```
app/src/main/java/com/notimind/lite/
├── NotiMindApp.kt
├── data/
│   ├── AppDatabase.kt
│   ├── NotificationDao.kt
│   └── NotificationEntity.kt
├── service/
│   ├── NotificationLoggerService.kt
│   └── BootReceiver.kt
├── ui/
│   ├── MainActivity.kt
│   ├── navigation/
│   │   └── NavGraph.kt
│   ├── screens/
│   │   ├── ActiveNotificationsScreen.kt
│   │   ├── LogHistoryScreen.kt
│   │   └── SettingsScreen.kt
│   └── theme/
│       └── Theme.kt
```
