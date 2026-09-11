// Several AGP Variant API members used below (outputFileName, artifacts.get,
// onVariants/selector for this variant-configuration style) are still
// marked @Incubating - meaning they work correctly today but the API
// surface could change in a future AGP release, not that anything here is
// broken. This is the standard, conventional way to suppress that specific
// warning category for the whole build script.
@file:Suppress("UnstableApiUsage")

import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.odiousapps.mx3launcher"
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.odiousapps.mx3launcher"
        minSdk = 29
        targetSdk = 37
        versionCode = 41
        versionName = "0.0.41"
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

// Copies the release .aab from AGP's default build output location
// (app/build/outputs/bundle/release/app-release.aab) to
// app/dist/<appName>-<versionName>.aab (already gitignored) -- a
// separate, deliberately-chosen destination outside the build/ directory,
// so it survives a clean build. (Originally this copied to app/release/
// instead: don't rename it back to that. app/release/ turned out to
// collide with Android Studio's own "Generate Signed Bundle" wizard,
// which independently remembers/defaults to a <module>/release/
// destination of its own and writes its own app-release.aab there on
// every signed-bundle build -- completely unrelated to this task, but
// landing in the exact same folder, which made it look like this task
// wasn't deleting the original when actually a second, IDE-driven copy
// was reappearing after each build. dist/ doesn't collide with anything.)
// Critically, this must run *after* AGP's own internal
// "produce...BundleIdeListingFile" task, which declares the bundle at
// its default name/location as one of its own inputs - deleting it any
// earlier fails that task's input validation with "file doesn't exist".
//
// Defined as a proper typed task class (not a closure passed to
// tasks.register) with Provider/Property-typed inputs: an earlier version
// captured the whole AGP `variant` object inside a doLast {} closure, and
// resolved variant.artifacts.get(SingleArtifact.BUNDLE) at execution time
// from within that closure. `variant` internally holds live references to
// Project, Configuration, and other Task objects (JavaCompile, etc.) --
// none of which the configuration cache is able to serialize, so every
// build failed to cache with errors naming exactly those types. Declaring
// bundleFile as a RegularFileProperty and wiring it from
// variant.artifacts.get(...) (itself a Provider<RegularFile>) at
// configuration time means only the resolved file path is ever captured --
// the task action itself never touches `variant` at all.
abstract class RenameBundleTask : DefaultTask() {
    @get:InputFile
    abstract val bundleFile: RegularFileProperty

    @get:OutputFile
    abstract val destinationFile: RegularFileProperty

    @TaskAction
    fun rename() {
        val file = bundleFile.get().asFile
        logger.lifecycle("renameBundle: source bundle at $file (exists=${file.exists()})")
        if (file.exists()) {
            val destination = destinationFile.get().asFile
            destination.parentFile.mkdirs()
            file.copyTo(destination, overwrite = true)
            logger.lifecycle("renameBundle: copied to $destination")
            // File.delete() never throws on failure, it just returns false --
            // check it explicitly so a failed delete (e.g. something else
            // still has the source file open/locked at this point) shows up
            // in the log instead of silently leaving the original behind
            // with no indication why.
            if (file.delete()) {
                logger.lifecycle("renameBundle: removed original $file")
            } else {
                logger.warn("renameBundle: could not delete original $file after copying -- it may be locked by another process; the copy at $destination is still correct")
            }
        } else {
            logger.lifecycle("renameBundle: expected bundle file not found at $file - skipping rename")
        }
    }
}

// Copies the release APK(s) from AGP's default build output location
// (app/build/outputs/apk/release/) to app/dist/ too, alongside the
// renamed bundle above. Unlike RenameBundleTask, this doesn't delete the
// originals -- there's no equivalent reason to (no known collision with
// anything else that writes to the APK output directory), so this is a
// plain copy, not a move.
//
// APK artifacts are exposed via SingleArtifact.APK: despite the name, this
// resolves to a *directory* (marked Artifact.ContainsMany in AGP's own
// docs), since a variant can in principle produce more than one APK
// (per-ABI splits, etc.), even though this project's release variant only
// ever produces one. Copying every .apk file found in that directory
// handles both cases without needing to special-case one vs. many, and
// without needing AGP's BuiltArtifactsLoader machinery (which exists for
// reading the accompanying metadata file precisely -- not needed here
// since a plain file-extension filter already skips it).
abstract class CopyApkTask : DefaultTask() {
    @get:InputFiles
    abstract val apkDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val destinationDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val srcDir = apkDirectory.get().asFile
        val destDir = destinationDirectory.get().asFile
        destDir.mkdirs()

        val apkFiles = srcDir.listFiles { f -> f.extension == "apk" } ?: emptyArray()
        logger.lifecycle("copyApk: found ${apkFiles.size} apk file(s) in $srcDir")
        apkFiles.forEach { apk ->
            val dest = File(destDir, apk.name)
            apk.copyTo(dest, overwrite = true)
            logger.lifecycle("copyApk: copied to $dest")
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val appName = "MX3Launcher"
        val versionName = variant.outputs.first().versionName
        val variantNameCapitalized = variant.name.replaceFirstChar { it.uppercase() }
        val ideListingTaskName = "produce${variantNameCapitalized}BundleIdeListingFile"

        // outputFileName only renames the file within AGP's default output
        // directory (app/build/outputs/apk/release/) -- getting it into
        // app/dist/ too still needs the separate copyApk task below, same
        // as the bundle.
        variant.outputs.forEach { output ->
            output.outputFileName.set("$appName-${versionName.get()}.apk")
        }

        val renameBundle = tasks.register("renameBundle$variantNameCapitalized", RenameBundleTask::class.java) {
            group = "build"
            description = "Copies the $variantNameCapitalized .aab to app/dist/$appName-<versionName>.aab"
            mustRunAfter(ideListingTaskName)
            bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
            destinationFile.set(layout.projectDirectory.file("dist/$appName-${versionName.get()}.aab"))
        }

        val copyApk = tasks.register("copyApk$variantNameCapitalized", CopyApkTask::class.java) {
            group = "build"
            description = "Copies the $variantNameCapitalized apk(s) to app/dist/"
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            destinationDirectory.set(layout.projectDirectory.dir("dist"))
        }

        // Hooks both onto their standard task graphs, so they also run
        // automatically from Android Studio's Build menu flows (which
        // invoke bundleRelease/assembleRelease directly), not just when
        // run explicitly by name.
        afterEvaluate {
            tasks.named("bundle$variantNameCapitalized") {
                finalizedBy(renameBundle)
            }
            tasks.named("assemble$variantNameCapitalized") {
                finalizedBy(copyApk)
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
