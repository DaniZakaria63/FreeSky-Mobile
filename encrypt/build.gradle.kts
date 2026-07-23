plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.wingsheep.encrypt"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES"
            )
        }
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.security.crypto)

    // ─────────────────────────────────────────────────────────────
    // MLS Group Encryption (Layer 3)
    // ─────────────────────────────────────────────────────────────
    // The guide specifies: space.zeroxv6:kotlin-mls:1.1.0
    // This library is not currently published to Maven Central.
    // Add it once available, or substitute with another MLS Android SDK.
    //
    // implementation("space.zeroxv6:kotlin-mls:1.1.0")
    //
    // ─────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────
    // Noise Protocol (Layer 0 — transport auth)
    // ─────────────────────────────────────────────────────────────
    // The guide specifies: nl.sanderdijkhuis:noise-kotlin
    // Available on Maven Central as version 1.0.1.
    //
    // implementation("nl.sanderdijkhuis:noise-kotlin:1.0.1")
    //
    // ─────────────────────────────────────────────────────────────

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
