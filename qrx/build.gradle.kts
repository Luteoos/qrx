plugins {
    `maven-publish`
    id("com.android.library")
    kotlin("android")
}

group = "com.github.luteoos"
version = "1.0.1"
val libVersion = version

apply(from = "../ktlint.gradle")

android {
    compileSdk = 31
    buildToolsVersion = "30.0.3"

    publishing{
        singleVariant("release"){
            withSourcesJar()
        }
        repositories{
            mavenLocal()
        }
    }

    defaultConfig {
        aarMetadata {
            minCompileSdk = 30
        }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
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
    api("com.google.mlkit:barcode-scanning:17.0.2")
}


publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "$group"
            artifactId = "qrx"
            version = "$libVersion"

            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("QRX")
                developers {
                    developer {
                        id.set("Luteoos")
                        name.set("Mateusz Lutecki")
                        email.set("mateusz.lutecki.it@gmail.com")
                        url.set("http://luteoos.github.io")
                    }
                }
            }
        }
    }
}
