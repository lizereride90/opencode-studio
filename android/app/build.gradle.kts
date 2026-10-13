plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.opencode.studio"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.opencode.studio"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += setOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").assets.srcDir("runtime-upstream/android/app/src/main/assets")
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Native libs must be extracted to nativeLibraryDir at install time:
        // the app symlinks + executes proot/pr-cli/busybox from app-private storage.
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(project(":proot-engine"))
    implementation("androidx.core:core-ktx:1.15.0")
}
