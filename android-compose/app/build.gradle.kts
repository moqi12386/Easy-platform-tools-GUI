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
        versionCode = 116
        versionName = "3.51.8"
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

    // 16 KB page size 适配（Android 15/16 要求）：
    // 不启用 legacy 打包，.so 以未压缩方式存放，AGP 8.5+ 会按 16 KB 对齐
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // 小米焦点通知（超级岛）DSL，Apache-2.0，API 在 Maven/JitPack
    implementation("com.xzakota.hyper.notification:focus-api:1.4")
    // Shizuku：用来临时掐 XMSF 网络，绕过小米超级岛白名单校验
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
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
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.github.kyant0:shapes:1.2.1")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
}