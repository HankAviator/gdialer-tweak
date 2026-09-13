plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.hankaviator.gdialertweak"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hankaviator.gdialertweak"
        minSdk = 30
        targetSdk = 35
        versionCode = 9
        versionName = "0.9.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
