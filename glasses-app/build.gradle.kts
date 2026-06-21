import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

var releaseSigningBaseDir: File? = null
val releaseSigningProperties = Properties().apply {
    val signingFile = System.getenv("JSOS_SIGNING_PROPERTIES")
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
        ?: rootProject.file("jsos-release.properties")
    if (signingFile.exists()) {
        releaseSigningBaseDir = signingFile.parentFile
        load(signingFile.inputStream())
    }
}

fun releaseSigningProperty(name: String): String? =
    releaseSigningProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { releaseSigningProperty(it) != null }

fun releaseSigningStoreFile(path: String): File {
    val candidate = File(path)
    if (candidate.isAbsolute) return candidate
    releaseSigningBaseDir?.resolve(project.name)?.resolve(path)?.takeIf { it.exists() }?.let { return it }
    releaseSigningBaseDir?.resolve(path)?.takeIf { it.exists() }?.let { return it }
    return file(path)
}

android {
    namespace = "com.jsos.glasses"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jsos.glasses"
        minSdk = 28  // Required for CXR-S SDK
        targetSdk = 34
        versionCode = 228
        versionName = "2.0.27-dynamic-sessions"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseSigningStoreFile(releaseSigningProperty("storeFile")!!)
                storePassword = releaseSigningProperty("storePassword")
                keyAlias = releaseSigningProperty("keyAlias")
                keyPassword = releaseSigningProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // PhoneConnectionService is not a traditional Android Service,
        // it's a helper class. Disable this specific lint check.
        disable += "Instantiatable"
    }
}

dependencies {
    implementation(project(":shared"))

    // Rokid CXR-S SDK (Glasses side)
    implementation("com.rokid.cxr:cxr-service-bridge:1.0")

    // Android Core (minimal for glasses)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Jetpack Compose (lightweight)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")
}
