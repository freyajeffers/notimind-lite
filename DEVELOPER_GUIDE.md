# NotiMind Lite Developer Guide

This document provides the technical onboarding necessary to build, test, and extend the NotiMind Lite codebase.

---

## 🛠️ Environment Setup

To contribute to NotiMind Lite, ensure your development environment meets the following requirements:

- **JDK**: Java Development Kit (JDK) 17 (Temurin recommended).
- **IDE**: Android Studio (Latest stable version).
- **SDK**: Android API Level 26 (minSdk) to API Level 36.
- **Build Tool**: Gradle (with KSP/Kapt for Room).

---

## 🚀 Build & Test

The project utilizes a strict CI/CD pipeline. Developers should replicate the CI environment locally before submitting changes.

### Common Commands
All commands should be run from the project root:

- **Run Unit Tests**: 
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Build Debug APK**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Full Verification (Test + Build)**:
  ```bash
  ./gradlew testDebugUnitTest assembleDebug
  ```

---

## 📦 Extending the Data Layer

NotiMind Lite uses Room for persistence. Because the app supports **Direct Boot**, the database is split between Device Protected (DE) and Credential Encrypted (CE) storage.

### Adding New Data Entities
1. **Define Entity**: Create a new class extending `RoomEntity` (if applicable) or a standard `@Entity` in `com.jeffers.notimindlite.data.local`.
2. **Update DAO**: Add the necessary `@Query` or `@Insert` methods to the corresponding DAO interface.
3. **Register Entity**: Add the class to the `@Database(entities = [...])` array in `AppDatabase.kt`.
4. **Migration Flow**:
   - Increment the version number in `AppDatabase.kt`.
   - Create a `Migration` object in `com.jeffers.notimindlite.util.DatabaseMigrator.kt` (e.g., `MIGRATION_16_17`).
   - Define the SQL `ALTER TABLE` or `CREATE TABLE` statements required.
   - Register the migration in the `AppDatabase` builder.

---

## 🧠 Extending Hybrid Search

The `HybridSearchEngine` combines Full-Text Search (FTS) and Vector Space Projection.

### Adding Search Domains
Search domains are managed by the `DynamicClusterManager`. To add a new semantic category (e.g., "Health"):

1. **Update Vocabulary**: Add relevant keywords and package names to the `DynamicClusterManager` vocabulary map.
2. **Refine Domain Inference**: Update the logic that maps `ApplicationInfo.CATEGORY_*` to the new domain.
3. **Verify Weights**: If the new domain is not appearing in results, adjust the $k$ constant in `com.jeffers.notimindlite.util.ReciprocalRankFusion.kt` to balance semantic vs. exact matches.

---

## ⚠️ Engineering Invariants
When modifying the code, adhere to these rules:
- **Main Thread Zero-I/O**: All database, file, and vector operations MUST occur on `Dispatchers.IO`.
- **Direct Boot Awareness**: Any component requiring early-boot access must be marked `android:directBootAware="true"` in the Manifest.
- **Memory Efficiency**: Use the `AppIconCache` for all bitmap operations to prevent OOM crashes.
