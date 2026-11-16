plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "vn.quyet.demnhip"
    compileSdk = 34

    defaultConfig {
        applicationId = "vn.quyet.demnhip"
        minSdk = 32          // Rokid RV101 chạy Android 12 (API 32)
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ký bằng khóa debug để cài thử ngay được; muốn phát hành thì thay signingConfig riêng
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
