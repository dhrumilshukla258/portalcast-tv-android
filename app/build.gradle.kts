plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.portalcast.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.portalcast.tv"
        minSdk = 21
        targetSdk = 34
        // CI release builds pass these from the git tag (.github/workflows/release.yml).
        versionCode = System.getenv("PORTALCAST_VERSION_CODE")?.toIntOrNull() ?: 3
        versionName = System.getenv("PORTALCAST_VERSION_NAME") ?: "0.3.0"
    }

    // Release key from the environment (GitHub secrets in CI, or exported locally).
    // Without it, release builds fall back to the debug key (fine for sideloading on
    // one machine, but an APK signed with another machine's debug key cannot update it).
    val releaseKeystore = System.getenv("PORTALCAST_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("PORTALCAST_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PORTALCAST_KEY_ALIAS")
                keyPassword = System.getenv("PORTALCAST_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Debug builds of Compose are several times slower (debuggable, no ART optimisation);
            // on a modest TV the debug APK lagged badly. Test on release builds.
            // Signed with the release key when PORTALCAST_KEYSTORE is set, else the debug key.
            // R8 (shrink + optimise + obfuscate) and resource shrinking: smaller APK,
            // faster cold start and execution on modest TV hardware.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseKeystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    // Compose (BOM-managed) + TV material
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material3:material3") // OutlinedTextField only (no TV equivalent)
    implementation("androidx.tv:tv-foundation:1.0.0-alpha11")
    implementation("androidx.tv:tv-material:1.0.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Media3 ExoPlayer for HLS/live + progressive VOD
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")

    // Networking + JSON
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    // Streaming JSON reader for the very large /api/v2/epg body (see data/net/EpgStream.kt)
    implementation("com.squareup.moshi:moshi:1.15.1")

    // Images
    implementation("io.coil-kt:coil-compose:2.6.0")

    // QR rendering for device-code pairing
    implementation("com.google.zxing:core:3.5.3")

    // Persistence
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Unit tests (JVM)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
