plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.android.compose.screenshot") version "0.0.1-alpha15"
}

android {
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
    namespace = "dev.draftingroom5"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.draftingroom5"
        minSdk = 37
        targetSdk = 37
        testInstrumentationRunner = "dev.draftingroom5.WidgetAuditInstrumentation"
        versionCode = providers.gradleProperty("appVersionCode").orElse("27006").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("0.27.4").get()
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

    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("androidTest").assets.srcDir("../docs/design/retirement-workspace/parity/shareworks")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Keep the independently audited provider SDK version pinned.
    implementation("com.plaid.link:sdk-core:5.5.5")
    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha15") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    debugImplementation("androidx.compose.ui:ui-tooling")
}
