// Declares plugins (apply false) so versions resolve once from the catalog
// rather than per module. No Kotlin Android plugin here — AGP 9 handles that
// built-in (see gradle.properties); kotlin.plugin.compose is the separate
// Compose Compiler plugin, still needed.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
