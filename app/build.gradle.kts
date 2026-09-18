plugins {
    id("com.android.application")
}

android {
    namespace = "com.rsteel.postomatsiz.saftest"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rsteel.postomatsiz.saftest"
        minSdk = 23
        targetSdk = 35
        versionCode = 8
        versionName = "3.5-standard-classic-ui-native-keyboard"
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
