// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()

    }
    dependencies {
        classpath ("com.android.tools.build:gradle:7.1.2")
        classpath ("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.30")
        classpath("com.google.devtools.ksp:symbol-processing-api:1.5.30-1.0.0-beta09")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.4.1")
        // NOTE: Do not place your application dependencies here; they belong
        // in the individual module build.gradle files
    }
}

plugins {
    `kotlin-dsl`
}

allprojects {
    repositories {
        google()
        maven(url = uri("https://jitpack.io"))
        mavenCentral()
    }
}