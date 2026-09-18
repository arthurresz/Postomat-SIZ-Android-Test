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
        versionCode = 15
        versionName = "3.12-standard-classic-ui-warehouse-compact"
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
