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
    // implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    // implementation("com.google.mlkit:text-recognition-korean:16.0.0")
    // implementation("com.google.mlkit:text-recognition:16.0.0")

    // TensorFlow Lite for YOLOv10 (Phase 3)
    // implementation("org.tensorflow:tensorflow-lite:2.14.0")
    // implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    // implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Testing
    testImplementation(libs.bundles.test)
    testImplementation(kotlinx.coroutines.test)
}
