plugins {
    // AGP 9 has built-in Kotlin support; the standalone kotlin-android plugin is gone.
    // See https://kotl.in/gradle/agp-built-in-kotlin
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
