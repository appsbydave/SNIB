import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing is supplied either by a gitignored keystore.properties (local) or by environment
// variables (CI secrets). When neither is present the release build is simply left unsigned, so a
// plain `assembleDebug` on a fresh checkout needs no secrets at all.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}
fun signingValue(prop: String, env: String): String? =
    keystoreProperties.getProperty(prop) ?: System.getenv(env)

val releaseStorePath: String? = signingValue("storeFile", "SNIB_KEYSTORE_PATH")
val hasReleaseSigning: Boolean = releaseStorePath != null && file(releaseStorePath).exists()

android {
    namespace = "com.snib.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.snib.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.9.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = signingValue("storePassword", "SNIB_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SNIB_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SNIB_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        // No applicationIdSuffix. Debug and release deliberately share one application id so they
        // cannot be installed side by side — two installs mean two accessibility services and two
        // databases, which looks exactly like a cross-process state bug. Swapping build type needs
        // an uninstall; do it before writing any real token, since that clears the household secret.
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
