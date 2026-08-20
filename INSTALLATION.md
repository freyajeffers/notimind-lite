# NotiMind Lite: Technical Installation Guide

This guide provides the step-by-step configuration required to build and deploy NotiMind Lite from source.

## 1. Prerequisites

Ensure the following development environment is configured:
- **Java Development Kit (JDK)**: Version 17 (Amazon Corretto or OpenJDK).
- **Android Studio**: Hedgehog (2023.1.1) or newer.
- **Android SDK**: API Level 26 (minSdk) and API Level 36 (Target SDK).
- **Physical Device**: A device with Google Play Services installed (required for Play Integrity API).

## 2. Google Cloud Platform (GCP) & Firebase Setup

NotiMind Lite relies on Firebase and the Play Integrity API for security notarization.

### Firebase Configuration
1. Create a new project in the [Firebase Console](https://console.firebase.google.com/).
2. Add an Android App to the project. Use the `applicationId` defined in `app/build.gradle.kts` (`com.jeffers.notimindlite`).
3. Enable **Firebase Authentication** (Google Sign-In provider).
4. Download the `google-services.json` configuration file.
5. Place the file in the `/app/` directory of the project root.

### Play Integrity API Setup
1. Navigate to the **Google Cloud Console** $\rightarrow$ **APIs & Services**.
2. Enable the **Play Integrity API**.
3. Link your Firebase project to the Google Play Console under **Setup $\rightarrow$ App Integrity**.

## 3. Keystore & Signing Configuration

Release builds require a valid signing keystore to pass the Play Integrity check.

### Generating a Keystore
Use the Android Studio `Build $\rightarrow$ Generate Signed Bundle/APK` wizard or the command line:
```bash
keytool -genkey -v -keystore notimind_release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias notimind_alias
```

### Configuring `build.gradle.kts`
Update the `signingConfigs` block in the app-level Gradle file:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("release/notimind_release.jks")
        storePassword = System.getenv("NOTIMIND_STORE_PASSWORD")
        keyAlias = "notimind_alias"
        keyPassword = System.getenv("NOTIMIND_KEY_PASSWORD")
    }
}
```
*Note: Do not hardcode passwords. Use environment variables or a `local.properties` file.*

## 4. Local Properties Setup

Create a `local.properties` file in the project root to handle environment-specific paths:
```properties
sdk.dir=/path/to/android-sdk
# Optional: Custom Notary Server URL for development
NOTARY_SERVER_URL=https://dev-notary.notimind.io
```

## 5. Build & Run

1. **Sync Project**: Click "Sync Project with Gradle Files" in Android Studio.
2. **Build**: Run `./gradlew assembleDebug` to generate the debug APK.
3. **Deploy**: Run `adb install app/build/outputs/apk/debug/app-debug.apk`.
4. **Permissions**: Manually grant **Notification Access** via `Settings $\rightarrow$ Special App Access`.
