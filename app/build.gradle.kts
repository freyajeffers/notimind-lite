plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.google.services)
  alias(libs.plugins.detekt)
  id("jacoco")
}

import java.io.File

android {
  namespace = "com.jeffers.notimindlite"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.jeffers.notimindlite"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0-lite"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // The debug keystore is required by AGP 9.3.2's
    // validateSigning{Debug,Release} tasks. Historically
    // Android Studio created it once per workstation and
    // every CI image shipped with a pre-baked one in
    // $ANDROID_USER_HOME. Both assumptions are no longer
    // true (Android Studio Iguana+ no longer creates one by
    // default, and GH Actions runner images ship without it),
    // so we materialize the standard Android debug keystore
    // via a Gradle task that runs before any signing
    // validation. The task is a no-op when the file is
    // already present (local dev). See `ensureDebugKeystore`
    // below.
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
    val releaseKeystorePath = System.getenv("SIGNING_STORE_FILE") ?: "${rootDir}/release.keystore"
    val releaseKeystoreFile = file(releaseKeystorePath)
    if (releaseKeystoreFile.exists()) {
      create("releaseConfig") {
        storeFile = releaseKeystoreFile
        storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "placeholder"
        keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "placeholder"
        keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "placeholder"
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("releaseConfig") ?: signingConfigs.getByName("debugConfig")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
      compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
          freeCompilerArgs.addAll(
              "-opt-in=kotlin.RequiresOptIn",
              "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
          )
      }
  }
  buildFeatures {
    compose = true
  }
  packaging {
    resources {
      excludes += listOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/INDEX.LIST",
        "/META-INF/DEPENDENCIES",
        "/META-INF/LICENSE.md",
        "/META-INF/LICENSE-notice.md"
      )
    }
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  lint {
    abortOnError = true
    checkDependencies = true
    baseline = file("lint-baseline.xml")
  }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("testCoverageReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(
            layout.buildDirectory.dir("tmp/kotlin-classes/debug")
        )
    )
    sourceDirectories.setFrom(
        files("${projectDir}/src/main/java")
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/test-debug-UnitTest.exec")
        }
    )
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // Firebase & Google Auth
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  // implementation(libs.firebase.crashlytics)
  implementation(libs.firebase.analytics)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.google.id)

  // WorkManager for Sync
  implementation(libs.androidx.work.runtime.ktx)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.room.testing)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  "ksp"(libs.androidx.room.compiler)
}

// Auto-generate the Android debug keystore if it does not exist.
// AGP 9.3.2's validateSigning{Debug,Release} tasks require the file
// at the path configured in `signingConfigs.debugConfig` (above).
// This task is a no-op when the keystore already exists (typical for
// local dev machines that have run Android Studio at least once), so
// it adds no measurable overhead to existing workflows. CI runners
// that never created one (e.g. fresh GH Actions images) will have
// the keystore materialized before signing validation runs.
//
// Configuration-cache note: this task opts OUT of the configuration
// cache via `notCompatibleWithConfigurationCache(...)` because the
// `Exec` task type's lambdas capture the enclosing build script
// (`this$0`), which is a script-object reference that Gradle 9.7+
// configuration cache refuses to serialize. The keystore check is
// idempotent and cheap (~1 ms on every project load), so we trade
// the small overhead of a no-op Exec invocation against the larger
// cost of reworking the entire build script for the cache. The
// Gradle documentation explicitly endorses this opt-out for tasks
// that fundamentally need closures over build-script state.
val debugKeystorePath: String = file("${rootDir}/debug.keystore").absolutePath

val ensureDebugKeystore = tasks.register<Exec>("ensureDebugKeystore") {
  description = "Materialize the standard Android debug keystore if absent."
  group = "build setup"
  notCompatibleWithConfigurationCache("Keystore generation needs script state; see comment above.")
  // `onlyIf` evaluates at task-graph time and bypasses the action
  // when the keystore is already present, so this is a no-op on
  // dev machines and a one-shot generator on fresh CI runners.
  onlyIf { !File(debugKeystorePath).exists() }
  commandLine(
    "keytool", "-genkeypair",
    "-keystore", debugKeystorePath,
    "-storepass", "android",
    "-keypass", "android",
    "-alias", "androiddebugkey",
    "-keyalg", "RSA",
    "-keysize", "2048",
    "-validity", "10000",
    "-dname", "CN=Android Debug,O=Android,C=US"
  )
}

// Wire ensureDebugKeystore into AGP's signing validation tasks so any
// build that triggers validateSigning* also runs our generator first.
afterEvaluate {
  tasks.matching { it.name.startsWith("validateSigning") }
    .configureEach { dependsOn(ensureDebugKeystore) }
}

