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
        versionCode = 11
        versionName = "0.11.0"
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
    implementation("com.google.android.material:material:1.14.0")
    compileOnly("de.robv.android.xposed:api:82")
}
