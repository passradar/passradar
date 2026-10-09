buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP 9 compiles Kotlin itself (built-in Kotlin) but ships with Kotlin 2.2.
        // Having a newer Kotlin Gradle plugin on the classpath upgrades the compiler.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
