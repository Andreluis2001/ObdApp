// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9's built-in Kotlin defaults to KGP 2.2.10. kvininfo 2.0.0 is compiled with
// Kotlin 2.3 metadata, so the compiler must be upgraded to read it.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}