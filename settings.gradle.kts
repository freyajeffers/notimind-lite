pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

// plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" } // removed due to resolution issues

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    maven {
      url = uri("https://jitpack.io")
      content {
        includeGroupByRegex("com\\.github.*")
      }
    }
  }
}

// Keep any dependency that is introduced by a plugin or transitive graph at a
// patched version. These rules are harmless when the module is absent and
// prevent vulnerable versions from entering any project configuration.
gradle.beforeProject {
  configurations.configureEach {
    resolutionStrategy.eachDependency {
      when (requested.group to requested.name) {
        "org.jdom" to "jdom2" -> useVersion("2.0.6.1")
        "org.apache.commons" to "commons-lang3" -> useVersion("3.18.0")
        "org.apache.httpcomponents" to "httpclient" -> useVersion("4.5.13")
        "com.google.guava" to "guava" -> useVersion("32.1.3-android")
      }
    }
  }
}

rootProject.name = "notimind lite"

include(":app")
