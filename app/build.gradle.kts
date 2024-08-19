plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.juby210.appinfofix"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.juby210.appinfofix"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = false
        resValues = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
