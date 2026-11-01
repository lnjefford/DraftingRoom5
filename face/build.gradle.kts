plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.draftingroom5.face"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.draftingroom5.face"
        minSdk = 33
        targetSdk = 37
        versionCode = providers.gradleProperty("wearVersionCode").orElse("290311").get().toInt()
        versionName = providers.gradleProperty("appVersionName").orElse("0.29.29").get()
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
}
