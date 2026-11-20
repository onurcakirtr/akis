plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.akis.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.akis.app"
        minSdk = 29
        targetSdk = 35
        // GitHub'daki release iş akışı sürümü etiketten verir (v1.2.0 → 1.2.0); yerelde 1.0
        versionCode = (findProperty("akisVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("akisVersionName") as String?) ?: "1.0"
    }

    // Release imzası: AKIS_KEYSTORE ortam değişkeni varsa (GitHub sırları) o anahtar, yoksa debug anahtarı
    val keystore = System.getenv("AKIS_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = keystore
                storePassword = System.getenv("AKIS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AKIS_KEY_ALIAS")
                keyPassword = System.getenv("AKIS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Kendi anahtarın yoksa kolay kurulum için debug anahtarıyla imzalanır
            signingConfig = if (keystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // cloudflared (uzaktan erişim) çalıştırılabilmesi için yerel kütüphane klasörüne açılmalı
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
    // Android 13+ uygulama dili menüsü res/values-<dil> klasörlerinden kendiliğinden oluşturulur
    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
    // Uygulama içi dil seçimi (Android 12 ve altı dahil)
    implementation("androidx.appcompat:appcompat:1.7.0")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
