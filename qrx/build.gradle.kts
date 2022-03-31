plugins {
    `maven-publish`
    id("com.android.library")
    kotlin("android")
}

apply(from = "../ktlint.gradle")

android {
    compileSdk = 31
    buildToolsVersion = "30.0.3"
    namespace = "io.github.luteoos.qrx"

    publishing{
        singleVariant("debug"){
            withSourcesJar()
        }
    }

    defaultConfig {
        aarMetadata {
            minCompileSdk = 30
        }
        version = 1
        minSdk = 21
        targetSdk = 31
    }

    buildFeatures {
        buildConfig = false
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures.dataBinding = true
}

dependencies {
    val cameraXVersion = "1.1.0-beta02"
    implementation(kotlin("stdlib", "1.5.31"))
    implementation("androidx.core:core-ktx:1.7.0")
    // CameraX core library using camera2 implementation
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    // CameraX Lifecycle Library
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    // CameraX View class
    implementation("androidx.camera:camera-view:$cameraXVersion")
    implementation("com.google.mlkit:barcode-scanning:17.0.2")
}

