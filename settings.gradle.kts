pluginManagement {
  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
  }
}

plugins {
  id("com.gradle.develocity") version "3.17.2"
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

develocity {
  buildScan {
    termsOfUseUrl = "https://gradle.com/terms-of-service"
    termsOfUseAgree = "yes"
    // TODO: workaround for https://github.com/gradle/gradle/issues/22879.
    val isCI = providers.environmentVariable("CI").isPresent
    publishing.onlyIf { isCI }
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
  }
}

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
