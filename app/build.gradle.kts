// Suppresses the @Incubating warning for the AGP Variant API used below
// (outputFileName, artifacts.get, onVariants/selector).
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
        versionCode = 48
        versionName = "0.0.48"
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
        jniLibs {
            // androidx.graphics:graphics-path ships this prebuilt without an NDK strip tool
            // available to match it, so the strip task can't touch it anyway - telling AGP to
            // keep its debug symbols outright stops it from trying (and logging the warning).
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
        }
    }
}

// Copies the release .aab to dist/ (gitignored, survives clean builds),
// not app/release/ (which collides with Android Studio's "Generate Signed
// Bundle" wizard). Must run after produce...BundleIdeListingFile, which needs
// the bundle at its default location first. Uses a typed task, not doLast{},
// since capturing the AGP `variant` object in a closure breaks
// configuration-cache serialization.
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
            // File.delete() returns false rather than throwing on failure —
            // check explicitly so a locked source file shows up in the log.
            if (file.delete()) {
                logger.lifecycle("renameBundle: removed original $file")
            } else {
                logger.warn("renameBundle: could not delete original $file after copying - it may be locked by another process; the copy at $destination is still correct")
            }
        } else {
            logger.lifecycle("renameBundle: expected bundle file not found at $file - skipping rename")
        }
    }
}

// Copies the release APK(s) to dist/ too. Unlike RenameBundleTask,
// keeps the originals — no collision to avoid here. SingleArtifact.APK is a
// directory (a variant can produce multiple APKs, e.g. per-ABI splits), so
// this copies every .apk found rather than assuming just one.
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

        // outputFileName only renames within AGP's default output directory;
        // getting it into dist/ still needs the copyApk task below.
        variant.outputs.forEach { output ->
            output.outputFileName.set("$appName-${versionName.get()}.apk")
        }

        val renameBundle = tasks.register("renameBundle$variantNameCapitalized", RenameBundleTask::class.java) {
            group = "build"
            description = "Copies the $variantNameCapitalized .aab to dist/$appName-<versionName>.aab"
            mustRunAfter(ideListingTaskName)
            bundleFile.set(variant.artifacts.get(SingleArtifact.BUNDLE))
            destinationFile.set(layout.projectDirectory.file("dist/$appName-${versionName.get()}.aab"))
        }

        val copyApk = tasks.register("copyApk$variantNameCapitalized", CopyApkTask::class.java) {
            group = "build"
            description = "Copies the $variantNameCapitalized apk(s) to dist/"
            apkDirectory.set(variant.artifacts.get(SingleArtifact.APK))
            destinationDirectory.set(layout.projectDirectory.dir("dist"))
        }

        // Hooked into the standard task graph so both also run from Android Studio's Build menu.
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
    // Pinned explicitly: tv-material is outside the compose-bom platform, so
    // its transitive version can conflict with the BOM's, producing
    // "internal in file" errors on classes like RowColumnParentData.
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.material.icons.extended)
    // Provides the Icon composable used in TopBar.kt — icons-extended only
    // ships the icon assets, not the composable that renders them.
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.zxing.core)
}
