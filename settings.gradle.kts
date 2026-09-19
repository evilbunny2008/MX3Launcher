// Suppresses the @Incubating warning for centralised repository declaration
// below -- works today, API may still change. Same suppression used in
// app/build.gradle.kts for the equivalent AGP Variant API warnings.
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
