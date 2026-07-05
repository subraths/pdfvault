plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.pdfvault"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pdfvault"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        vectorDrawables { useSupportLibrary = true }

        // Base URL of the PdfVault backend (from `serverless deploy`). Set BACKEND_BASE_URL in
        // gradle.properties / local.properties (or -P). Empty = cloud sync disabled (local-only).
        val backendBaseUrl = (project.findProperty("BACKEND_BASE_URL") as String? ?: "").trimEnd('/')
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
    }

    signingConfigs {
        // Release signing is driven entirely by env vars (or -P properties) so no secret ever
        // lives in the repo. CI decodes a base64 keystore to a file and points KEYSTORE_FILE at
        // it. When nothing is supplied (local dev, or CI without the signing secrets configured)
        // the storeFile stays null and release builds fall back to the debug key below — the APK
        // is still installable for testing, just not signed with the real upload key.
        create("release") {
            val storePath = (System.getenv("KEYSTORE_FILE")
                ?: project.findProperty("KEYSTORE_FILE") as String?)?.takeIf { it.isNotBlank() }
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: project.findProperty("KEYSTORE_PASSWORD") as String?
                keyAlias = System.getenv("KEY_ALIAS") ?: project.findProperty("KEY_ALIAS") as String?
                keyPassword = System.getenv("KEY_PASSWORD") ?: project.findProperty("KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) releaseSigning else signingConfigs.getByName("debug")
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

    packaging {
        resources {
            // The AWS SDK ships duplicate metadata files; drop them to avoid merge clashes.
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.aws.s3)
    implementation(libs.pdfbox.android)
    implementation(libs.telephoto.zoomable)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.documentfile)

    debugImplementation(libs.androidx.ui.tooling)
}
