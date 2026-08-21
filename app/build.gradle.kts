plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.google.services)
  alias(libs.plugins.detekt)
  id("jacoco")
}

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
  kotlinOptions {
      jvmTarget = "17"
      freeCompilerArgs += listOf(
          "-opt-in=kotlin.RequiresOptIn",
          "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
      )
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

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  "ksp"(libs.androidx.room.compiler)
}

