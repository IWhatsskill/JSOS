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
    namespace = "com.jsos.watch"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.jsos.watch"
        minSdk = 28
        targetSdk = 34
        versionCode = 5
        versionName = "0.5-watch-ui"
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
}

dependencies {
    implementation(project(":shared"))

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.wear.compose:compose-material3:1.6.2")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
