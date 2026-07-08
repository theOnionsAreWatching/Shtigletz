plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.theonionsarewatching.shtigletz"
    compileSdk = 34

    defaultConfig {
        // minSdk 23: androidx.security-crypto (encrypted credential storage) requires API 23+.
        minSdk = 23
        targetSdk = 34
        versionCode = 12
        versionName = "0.7.1"
    }

    // Three apps, one codebase. The dial is "how much of the message gets
    // through": kosher = text only; plus = + attachments; pro = + images.
    // Each flavor supplies its own FlavorConfig.kt (src/<flavor>/java/...)
    // and its own applicationId so all three install side by side.
    flavorDimensions += "policy"
    productFlavors {
        create("kosher") {
            dimension = "policy"
            applicationId = "io.github.theonionsarewatching.shtigletz"
            resValue("string", "app_name", "D-Mail Kosher")
        }
        create("plus") {
            dimension = "policy"
            applicationId = "io.github.theonionsarewatching.honeymustard"
            resValue("string", "app_name", "D-Mail Plus")
        }
        create("pro") {
            dimension = "policy"
            applicationId = "io.github.theonionsarewatching.onegshabbos"
            resValue("string", "app_name", "D-Mail Pro")
        }
        create("gefilte") {
            dimension = "policy"
            applicationId = "io.github.theonionsarewatching.gefilte"
            resValue("string", "app_name", "D-Mail Max")
        }
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
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Jakarta Mail (javax.mail) Android build: IMAP + SMTP.
    // Chosen for its lazy IMAP fetching: reading only text parts means
    // attachment bytes are never requested from the server at all.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
}
