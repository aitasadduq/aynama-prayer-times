plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.aynama.prayertimes.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aynama.prayertimes"
        // Wear OS 3 and later. Older watches run a different app model entirely.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // The prayer domain — Adhan wrapper, Room entities, and the countdown and naming rules —
    // is the same code the phone runs. The watch must not re-derive any of it.
    implementation(project(":shared-logic"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)

    implementation(libs.wear.tiles)
    implementation(libs.wear.tiles.material)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.material)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.watchface.complications.data.source)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.tooling)
}
