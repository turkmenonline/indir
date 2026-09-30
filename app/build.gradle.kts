import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.gdl"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.gdl"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "2.1"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("io.coil-kt:coil:2.6.0")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material3:material3")
    implementation("com.github.junkfood02.youtubedl-android:ffmpeg:0.14.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { stream ->
            load(stream)
        }
    }
}
val isWindows = System.getProperty("os.name").lowercase().contains("windows")
val defaultPython = if (isWindows) "python" else "python3"
val pythonExec = localProperties.getProperty("python.path") ?: defaultPython

chaquopy {
    defaultConfig {
        buildPython(pythonExec)
        version = "3.14"
        pip {
            install("gallery-dl")
            install("yt-dlp")
            install("certifi")
        }
    }
}
