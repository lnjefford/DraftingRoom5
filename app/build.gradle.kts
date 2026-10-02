plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.android.compose.screenshot") version "0.0.1-alpha16"
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
        versionCode = providers.gradleProperty("phoneVersionCode").orElse("290030").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("0.29.1").get()
        buildConfigField("String", "UPDATE_DATE", "\"October 1, 2026\"")
        buildConfigField("boolean", "PLAY_DISTRIBUTION", "false")
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
        create("play") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("distribution")
            buildConfigField("boolean", "PLAY_DISTRIBUTION", "true")
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").kotlin.directories.add(rootProject.file("shared/watchprotocol").absolutePath)
    sourceSets.getByName("androidTest").assets.srcDir("../docs/design/retirement-workspace/parity/shareworks")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    constraints {
        implementation(libs.androidx.fragment) {
            because("Keep transitive Fragment compatible with Activity Result APIs")
        }
        implementation(libs.google.guava) {
            because("Fix GHSA-5mg8-w23w-74h3 and GHSA-7g45-4rm6-3mm3 in transitive Guava")
        }
    }
    // Reevaluate SDK releases during weekly maintenance, including native API migrations.
    implementation("com.plaid.link:sdk-core:6.2.2")
    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha16") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
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
    implementation(libs.google.play.services.wearable)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
