plugins {
    alias(libs.plugins.android.application)
}

import java.util.Properties

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasKeystoreProperties = keystorePropertiesFile.exists()

if (hasKeystoreProperties) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

val releaseStoreFilePath = (keystoreProperties["storeFile"] as String?)?.trim().orEmpty()
val releaseStorePassword = (keystoreProperties["storePassword"] as String?)?.trim().orEmpty()
val releaseKeyAlias = (keystoreProperties["keyAlias"] as String?)?.trim().orEmpty()
val releaseKeyPassword = (keystoreProperties["keyPassword"] as String?)?.trim().orEmpty()
val releaseStoreFile = if (releaseStoreFilePath.isNotEmpty()) file(releaseStoreFilePath) else null

val hasReleaseKeystore = hasKeystoreProperties &&
    releaseStoreFile != null &&
    releaseStoreFile.exists() &&
    releaseStorePassword.isNotEmpty() &&
    releaseKeyAlias.isNotEmpty() &&
    releaseKeyPassword.isNotEmpty()

if (!hasReleaseKeystore) {
    logger.warn("Valid release keystore config not found. release build will use debug signing for local testing only.")
}

android {
    namespace = "com.example.busapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.busapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
