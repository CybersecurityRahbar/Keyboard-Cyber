plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stableKeystorePath = System.getenv("KEYSTORE_PATH")
val stableKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
val stableKeyAlias = System.getenv("KEY_ALIAS")
val stableKeyPassword = System.getenv("KEY_PASSWORD")

val hasStableSigning = !stableKeystorePath.isNullOrBlank() &&
    !stableKeystorePassword.isNullOrBlank() &&
    !stableKeyAlias.isNullOrBlank() &&
    !stableKeyPassword.isNullOrBlank()

android {
    namespace = "com.cybersecurityrahbar.keyboardcyber"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cybersecurityrahbar.keyboardcyber"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = file(stableKeystorePath!!)
                storeType = "PKCS12"
                storePassword = stableKeystorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // CI supplies the project's persistent key through GitHub Actions Secrets.
            // Local development without secrets keeps the normal local debug signing key.
            if (hasStableSigning) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
