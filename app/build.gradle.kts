
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.youtubektv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.youtubektv"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.1"

        val apiKey = (project.findProperty("YOUTUBE_API_KEY") as String?) ?: ""
        buildConfigField("String", "YOUTUBE_API_KEY", "\"$apiKey\"")
    }

    flavorDimensions += "device"

    productFlavors {
        create("mobile") {
            dimension = "device"
            applicationIdSuffix = ".mobile"
        }
        create("tv") {
            dimension = "device"
            applicationIdSuffix = ".tv"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
