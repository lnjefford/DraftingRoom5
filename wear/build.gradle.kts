plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.android.compose.screenshot") version "0.0.1-alpha15"
}

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    namespace = "dev.draftingroom5.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.draftingroom5"
        minSdk = 30
        targetSdk = 37
        versionCode = providers.gradleProperty("appVersionCode").orElse("28002").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("0.28.0").get()
    }

    signingConfigs {
        create("distribution") {
            val signingFile = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!signingFile.isNullOrBlank()) {
                storeFile = file(signingFile)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") { signingConfig = signingConfigs.getByName("distribution") }
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main") {
        kotlin.directories.add(rootProject.file("shared/watchprotocol").absolutePath)
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.androidx.wear.compose.bom))
    implementation(libs.androidx.wear.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.google.play.services.wearable)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha15") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
