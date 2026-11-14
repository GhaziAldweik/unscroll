import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Signing details live outside git in keystore.properties (see keystore.properties.example).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasKeystore = keystoreProperties.getProperty("storeFile")?.let { file(it).exists() } == true
if (!hasKeystore) {
    logger.warn("keystore.properties or the keystore is missing: this build will NOT install over existing installs.")
}

android {
    namespace = "dev.ghazi.igfocus"
    compileSdk = 37

    defaultConfig {
        // Never change the application ID: Android treats a new ID as a different app.
        applicationId = "dev.ghazi.igfocus"
        minSdk = 34
        targetSdk = 37
        versionCode = 4
        versionName = "1.1.0"
    }

    signingConfigs {
        if (hasKeystore) {
            create("fixed") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Debug and release share one key so either installs over the other and keeps settings.
        debug {
            if (hasKeystore) signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            if (hasKeystore) signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
