plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.aynama.prayertimes.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.adhan)
    testImplementation(libs.junit)
}
