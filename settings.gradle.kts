pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// AGP and its tooling still request vulnerable transitive libraries. These are
// minimum versions, not forced pins: newer upstream versions remain selectable.
// Reevaluate these floors with each weekly AGP/screenshot-tooling upgrade.
gradle.beforeProject {
    val securityFloors = listOf(
        "org.bouncycastle:bcprov-jdk18on:1.86",
        "org.bouncycastle:bcpkix-jdk18on:1.86",
        "org.bouncycastle:bcutil-jdk18on:1.86",
        "org.bitbucket.b_c:jose4j:0.9.7",
        "org.jdom:jdom2:2.0.6.1",
        "org.apache.commons:commons-lang3:3.21.0",
        "org.apache.httpcomponents:httpclient:4.5.14",
    )
    val buildscriptSecurityFloors = securityFloors + "com.google.guava:guava:33.7.2-jre"
    buildscript.dependencies.constraints {
        buildscriptSecurityFloors.forEach { coordinate ->
            add("classpath", coordinate) {
                because("Patch transitive build-tool advisories; see docs/dependency-maintenance/2026-09-28.md")
            }
        }
    }
    val projectDependencies = dependencies
    configurations.configureEach {
        if (name == "androidLintTool" || name == "_internal-screenshot-validation-junit-engine") {
            projectDependencies.constraints.add(name, "com.google.guava:guava:33.7.2-jre") {
                because("Patch transitive Guava in lint and screenshot tooling; reevaluate with AGP updates")
            }
        }
        if (isCanBeDeclared) {
            val configurationName = name
            securityFloors.forEach { coordinate ->
                projectDependencies.constraints.add(configurationName, coordinate) {
                    because("Keep standalone tooling configurations on patched transitive libraries")
                }
            }
        }
    }
}

rootProject.name = "DraftingRoom5"
include(":app")
include(":wear")
