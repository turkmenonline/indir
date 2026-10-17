import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.gdl"
    compileSdk = 36
    defaultConfig {
        // Yalnızca kendi cihazınızda denemek için: local.properties içine
        // nowbar.package=<paket adı> yazarsanız uygulama o paket adıyla derlenir.
        // local.properties git'e girmez, yani bu değer repoya çıkmaz.
        applicationId = localProps.getProperty("nowbar.package")?.takeIf { it.isNotBlank() } ?: "com.example.gdl"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "2.1"
        // Sadece gerçek arm64 cihazlar için (ör. Samsung A35 5G / SM-A356E).
        // x86_64 yalnızca emülatörde test etmek isteyenlere lazım; kaldırılınca
        // Chaquopy/Python, ffmpeg gibi tüm native .so'lar tek ABI'ye iner ve APK küçülür.
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildFeatures {
        compose = true
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.github.Dimezis:BlurView:version-2.0.4")
    // Not: "youtubedl-android:library" (yt-dlp/aria2c/python-for-android'ı ABI başına
    // kendi içinde paketleyen asıl büyük modül) kodda hiç kullanılmıyor — indirme işini
    // zaten Chaquopy üzerinden Python (dl.py) yapıyor. Sadece ffmpeg modülü (altyazı
    // gömme için MainActivity/DownloadService'te kullanılıyor) kalsın.
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

val localPython = localProps.getProperty("python.path")

chaquopy {
    defaultConfig {
        // Eğer yerel bilgisayarda tanımlı bir Python varsa onu kullan, yoksa (GitHub Actions / Linux ise) buildPython çağırma, sistemdeki python3'ü otomatik bulsun:
        if (!localPython.isNullOrBlank() && file(localPython).exists()) {
            buildPython(localPython)
        }
        version = "3.14"
        pip {
            install("gallery-dl")
            install("yt-dlp")
            install("certifi")
            install("urllib3<2.0.0")
        }
    }
}
