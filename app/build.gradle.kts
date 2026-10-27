import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.rishi.kiranaledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rishi.kiranaledger"
        minSdk = 26 // Android 8.0
        targetSdk = 35
        versionCode = 9
        versionName = "1.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Only ship our three languages, not the ~85 that libraries bring along.
        resourceConfigurations += listOf("en", "hi", "b+hi+Latn")
    }

    // Release signing key: keep kirana-release.keystore and keystore.properties safe (and backed up) -
    // every future update for the shop's phone must be signed with this same key.
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        // Debug/test builds are a separate app ("Kirana TEST") with their own empty data,
        // so testing on the shop's phone never touches the real khata.
        debug {
            applicationIdSuffix = ".qa"
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
    }
    androidResources {
        // Lets Android offer Hinglish / English / Hindi as per-app languages.
        generateLocaleConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The upgrade (migration) tests read the saved schemas.
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0") // per-app language switching
    implementation("androidx.biometric:biometric:1.1.0") // app lock
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:$room")
}
