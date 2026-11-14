plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.drumlivepro.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drumlivepro.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.6"
        manifestPlaceholders["appLabel"] = "Drum Live Pro v$versionName"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "sf2" }
}
