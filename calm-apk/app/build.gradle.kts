plugins {
    id("com.android.application")
}

android {
    namespace = "com.aviv.calmschedule"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aviv.calmschedule"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
