plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.theonionsarewatching.shtigletz"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.theonionsarewatching.shtigletz"
        // minSdk 23: androidx.security-crypto (encrypted credential storage) requires API 23+.
        minSdk = 23
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
    }

    // Release signing is injected by CI (see .github/workflows/build.yml).
    // If no keystore env vars are present, release builds are unsigned and CI skips them.
    val keystorePath = System.getenv("KEYSTORE_FILE") ?: "/tmp/release.keystore"
    val hasKeystore = System.getenv("KEYSTORE_PASSWORD") != null && file(keystorePath).exists()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md",
            "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/NOTICE.md",
            "META-INF/DEPENDENCIES"
        )
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Jakarta Mail (javax.mail) Android build: IMAP + SMTP.
    // Chosen for its lazy IMAP fetching: reading only text parts means
    // attachment bytes are never requested from the server at all.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}
