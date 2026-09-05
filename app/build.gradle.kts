import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Secrets come from a git-ignored `.env` in the project root (see .env.example).
// Missing file or key is not an error: the app builds and tells the user to configure it.
val env = Properties().apply {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) envFile.inputStream().use { load(it) }
}
fun envOrEmpty(key: String, default: String = "") =
    (env.getProperty(key) ?: System.getenv(key) ?: default).trim()

android {
    namespace = "com.example.ai_camera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ai_camera"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // ONNX Runtime ships a native library per ABI and all four together are ~74MB. Real
            // devices are arm64; x86_64 is kept only so the emulator can still run this.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        buildConfigField("String", "GEMINI_API_KEY", "\"${envOrEmpty("GEMINI_API_KEY")}\"")
        buildConfigField(
            "String",
            "GEMINI_MODEL",
            "\"${envOrEmpty("GEMINI_MODEL", "gemini-2.5-flash")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    androidResources {
        // The quantized model is already compressed; zipping it again only slows the build and
        // the first-run copy out of assets.
        noCompress += "onnx"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.exifinterface)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}