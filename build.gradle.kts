plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 compiles Kotlin itself (built-in Kotlin); declaring the Kotlin plugin here, never applied,
    // puts the catalog's Kotlin on the build classpath, so AGP and the Compose compiler use that version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
