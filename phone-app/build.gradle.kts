import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Load local build properties for release signing fallback.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
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
    releaseSigningProperties.getProperty(name)
        ?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty("jsos.release.$name")?.takeIf { it.isNotBlank() }

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
    namespace = "com.jsos.phone"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jsos.phone"
        minSdk = 28  // Required by CXR-M SDK
        targetSdk = 34
        versionCode = 210
        versionName = "2.0.10-openai-tts"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.md"
            )
        }
    }
}

dependencies {
    implementation(project(":shared"))

    // Rokid CXR-M SDK (Phone side): Bluetooth bridge, device control, camera, wake, voice events
    implementation("com.rokid.cxr:client-m:1.2.1")
    implementation(files("libs/client-l-1.0.1-jsos-stripped.aar"))

    // Android Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Networking for WebSocket/SSH
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Speech Recognition
    implementation("androidx.core:core-ktx:1.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Ed25519 signing (Android's bundled BouncyCastle doesn't include EdDSA)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
