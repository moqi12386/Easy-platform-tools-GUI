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
        versionCode = 30
        versionName = "3.2.0"
    }

    // 签名从环境变量读；没配置就出未签名包，方便 clone 后直接编译
    signingConfigs {
        create("easypt") {
            val ks = System.getenv("EASYPT_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("EASYPT_STORE_PASS") ?: "android"
                keyAlias = System.getenv("EASYPT_KEY_ALIAS") ?: "easypt"
                keyPassword = System.getenv("EASYPT_KEY_PASS") ?: "android"
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".compose"
            if (signingConfigs.getByName("easypt").storeFile != null) {
                signingConfig = signingConfigs.getByName("easypt")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            applicationIdSuffix = ".compose"
            if (signingConfigs.getByName("easypt").storeFile != null) {
                signingConfig = signingConfigs.getByName("easypt")
            }
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
    // 图标必须显式引入：material3 只把它当编译期传递依赖，缺了会在运行时 NoClassDefFoundError
    implementation("androidx.compose.material:material-icons-core:1.6.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
}
