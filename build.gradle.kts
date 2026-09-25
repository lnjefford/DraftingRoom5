plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// Windows verification sets this outside OneDrive to prevent sync locks on generated files.
providers.environmentVariable("DR5_BUILD_ROOT").orNull?.let { externalRoot ->
    layout.buildDirectory.set(file("$externalRoot/root"))
    subprojects {
        val directoryName = path.trim(':').replace(':', '-')
        layout.buildDirectory.set(file("$externalRoot/$directoryName"))
    }
}
