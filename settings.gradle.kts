pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "VEL"
include(":app")

// Resolución dinámica del directorio del módulo :app para máxima tolerancia a rutas
val appDir = when {
    file("app").exists() -> file("app")
    file("android-vel/app").exists() -> file("android-vel/app")
    file("vel-android-nativo/app").exists() -> file("vel-android-nativo/app")
    else -> file("app")
}
project(":app").projectDir = appDir

