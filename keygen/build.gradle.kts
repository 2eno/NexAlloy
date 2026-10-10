plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.nexalloy.keygen"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.nexalloy.keygen"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
