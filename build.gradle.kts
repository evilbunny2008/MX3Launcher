// Top-level build file -- individual module build.gradle.kts files apply
// specific plugins; this only declares them (apply false) so version
// numbers are resolved once, from the version catalog, rather than
// duplicated per module.
//
// No separate Kotlin Android plugin declared here -- AGP 9's built-in
// Kotlin support handles that directly now (see gradle.properties for
// the migration note). org.jetbrains.kotlin.plugin.compose is still
// needed and kept -- that's the Compose Compiler plugin specifically,
// a separate concern from base Kotlin compilation.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
