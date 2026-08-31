// Several AGP Variant API members used below (outputFileName, artifacts.get,
// onVariants/selector for this variant-configuration style) are still
// marked @Incubating - meaning they work correctly today but the API
// surface could change in a future AGP release, not that anything here is
// broken. This is the standard, conventional way to suppress that specific
// warning category for the whole build script.
@file:Suppress("UnstableApiUsage")

import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.odiousapps.mx3launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.odiousapps.mx3launcher"
        // Android 14 (API 34).
        minSdk = 26
        targetSdk = 37
        versionCode = 39
        versionName = "0.0.39"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Renames the release .aab from AGP's default "app-release.aab" to
// "<appName>-<versionName>.aab", in place, within app/release/ (already
// gitignored) - which turns out to already be this project's actual bundle
// output location (confirmed by an AGP validation error naming that exact
// path as a declared input elsewhere), not a separate custom destination to
// copy into as originally assumed. Critically, this must run *after* AGP's
// own internal "produce...BundleIdeListingFile" task, which declares the
// bundle at its default name as one of its own inputs - renaming (or
// deleting) it any earlier fails that task's input validation with "file
// doesn't exist", which is what happened when this was ordered the other
// way around.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val appName = "MX3Launcher"
        val versionName = variant.outputs.first().versionName
        val variantNameCapitalized = variant.name.replaceFirstChar { it.uppercase() }
        val ideListingTaskName = "produce${variantNameCapitalized}BundleIdeListingFile"

        // APK variant outputs support a directly settable filename, unlike
        // the bundle (AAB) case above - no separate rename/copy task needed.
        variant.outputs.forEach { output ->
            output.outputFileName.set("$appName-${versionName.get()}.apk")
        }

        val renameBundle = tasks.register("renameBundle$variantNameCapitalized") {
            group = "build"
            description = "Renames the $variantNameCapitalized .aab in place to $appName-<versionName>.aab"
            mustRunAfter(ideListingTaskName)
            doLast {
                val bundleFile = variant.artifacts.get(SingleArtifact.BUNDLE).get().asFile
                if (bundleFile.exists()) {
                    val renamedFile = File(bundleFile.parentFile, "$appName-${versionName.get()}.aab")
                    bundleFile.copyTo(renamedFile, overwrite = true)
                    bundleFile.delete()
                } else {
                    println("Expected bundle file not found at $bundleFile - skipping rename")
                }
            }
        }
        // Hooks the rename onto the standard "bundle" task graph, so it also
        // runs automatically from Android Studio's Build > Generate Signed
        // App Bundle flow (which invokes bundleRelease directly), not just
        // when this task is run explicitly by name.
        afterEvaluate {
            tasks.named("bundle$variantNameCapitalized") {
                finalizedBy(renameBundle)
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    // Explicitly requested (pinned to the same BOM) rather than left to
    // whatever version tv-material transitively pulls in on its own --
    // tv-material isn't part of the compose-bom platform (separate
    // androidx.tv group), so without this, two different resolved
    // versions of compose-foundation can end up on the classpath at
    // once. That split is exactly what produces "internal in file"
    // errors on Compose's internal classes like RowColumnParentData --
    // not a mistake in how .weight() itself is called.
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons.extended)
    // The androidx.compose.material3.Icon composable used in TopBar.kt
    // lives here -- this was missing entirely before (icons-extended
    // only provides the icon assets themselves, not the Icon composable
    // that renders them), which is exactly why the import was
    // unresolved: the class it pointed at was real, but the library
    // providing it was never actually on the classpath.
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.zxing.core)
}
