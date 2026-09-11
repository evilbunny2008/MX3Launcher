// dependencyResolutionManagement's repositoriesMode/RepositoriesMode/
// FAIL_ON_PROJECT_REPOS below are all part of Gradle's centralised
// repository declaration feature, still marked @Incubating -- meaning
// they work correctly today, but the API surface could change in a
// future Gradle release, not that anything here is broken. Same
// suppression already applied to both projects' app/build.gradle.kts
// for the equivalent AGP Variant API warnings.
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
