plugins {
    id("mihon.library")
    id("mihon.library.compose")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "mihon.feature.translation"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

dependencies {
    // Core dependencies
    implementation(projects.core.common)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(projects.i18n)
    implementation(projects.sourceApi)

    // Coroutines
    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Serialization (for API communication)
    implementation(kotlinx.bundles.serialization)

    // Compose UI
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.ui.tooling.preview)
    debugImplementation(compose.ui.tooling)

    // Networking (for translation APIs)
    implementation(libs.bundles.okhttp)

    // Preferences
    implementation(libs.preferencektx)

    // Injekt (DI)
    implementation(libs.injekt)

    // ML Kit for OCR (Phase 2)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")

    // ML Kit Module Install API (for on-demand model download)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // PaddleOCR for high-accuracy OCR (Phase 2 - Alternative OCR)
    // Paddle Lite SDK - Manual installation required (not in Maven Central)
    //
    // ⚠️ SETUP REQUIRED: Download Paddle Lite AAR files manually
    // 1. Download from: https://github.com/PaddlePaddle/Paddle-Lite/releases
    //    File: paddle_lite_libs_v2.13_android.tar.gz (~50MB)
    // 2. Extract and copy to: translation/libs/
    //    - paddle_lite_java.aar
    //    - paddle_lite_jni.aar
    // 3. Uncomment lines below to enable Paddle OCR
    //
    // implementation(files("libs/paddle_lite_java.aar"))
    // implementation(files("libs/paddle_lite_jni.aar"))
    //
    // See: translation/docs/PADDLEOCR_SETUP_GUIDE.md for full instructions

    // OkHttp for model downloading (already included but ensuring availability)
    // implementation(libs.bundles.okhttp)

    // TensorFlow Lite for YOLOv10 (Phase 3)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")

    // Testing
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
