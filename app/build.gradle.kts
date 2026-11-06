plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.quietmoments.diary"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.quietmoments.universeoffline"
        minSdk = 26
        targetSdk = 35
        versionCode = 240
        versionName = "2.4.0-safe-migration"
    }
    val stableSigningPath = System.getenv("QM_KEYSTORE_PATH")
    signingConfigs {
        if (!stableSigningPath.isNullOrBlank()) {
            create("stableRelease") {
                storeType = "PKCS12"
                storeFile = file(stableSigningPath)
                storePassword = System.getenv("QM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("QM_KEY_ALIAS")
                keyPassword = System.getenv("QM_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    buildTypes {
        debug {
            // Install preview beside existing versions; never replace private data without a stable key.
            applicationIdSuffix = ".preview240"
            versionNameSuffix = "-preview"
        }
        release {
            isMinifyEnabled = false
            if (!stableSigningPath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stableRelease")
            }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":universe"))
    implementation("com.google.android.gms:play-services-auth:22.0.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.6")
    testImplementation("junit:junit:4.13.2")
}
