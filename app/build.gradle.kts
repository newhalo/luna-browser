plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.luna.browser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.luna.browser"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.3.1"
    }

    // Signing
    //  - "dev": a PUBLIC development key (app/dev.keystore, password "lunabrowser"). Every contributor and
    //    CI run signs debug and unsigned-fallback builds with it, so builds install over each other without
    //    wiping app data. It proves nothing about who built an APK: never distribute builds signed with it
    //    as official releases.
    //  - "release": the maintainer's private key, supplied through environment variables
    //    (LUNA_KEYSTORE_FILE, LUNA_KEYSTORE_PASSWORD, LUNA_KEY_ALIAS, LUNA_KEY_PASSWORD). It is never in the repo.
    signingConfigs {
        create("dev") {
            storeFile = file("dev.keystore")
            storePassword = "lunabrowser"
            keyAlias = "luna"
            keyPassword = "lunabrowser"
        }
        System.getenv("LUNA_KEYSTORE_FILE")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("LUNA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LUNA_KEY_ALIAS")
                keyPassword = System.getenv("LUNA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
            // A separate app id, so a debug build installs NEXT TO the release app (own data, own launcher
            // icon "Luna (debug)") instead of fighting it over the signing key.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("dev")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.webkit:webkit:1.11.0")
    // Installs the baseline profiles shipped by Compose & AndroidX, so ART compiles the hot startup
    // and scrolling code ahead of time instead of interpreting it on the TV.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    // QR code for pairing the phone remote (encoder only, no camera code).
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    // The Android SDK stubs have no org.json implementation, which unit tests need.
    testImplementation("org.json:json:20240303")
}
