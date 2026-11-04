plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.easypt.gui"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.easypt.gui"
        minSdk = 26
        targetSdk = 37
        versionCode = 68
        versionName = "3.17.2"
    }

    signingConfigs {
        create("easypt") {
            storeFile = file("C:/Users/35923/Desktop/安卓调试台/android/easypt.keystore")
            storePassword = "android"
            keyAlias = "easypt"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("easypt")
            applicationIdSuffix = ".compose"
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("easypt")
            applicationIdSuffix = ".compose"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui:1.8.3")
    implementation("androidx.compose.foundation:foundation:1.8.3")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.compose.material:material-icons-core:1.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
}