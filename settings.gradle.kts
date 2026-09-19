// Suppresses the @Incubating warning for the centralised repository
// declaration below (same suppression as app/build.gradle.kts, for its
// AGP Variant API warnings).
@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MX3 Launcher"
include(":app")
