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
        // [CORREÇÃO] Usando a sintaxe correta do Kotlin DSL: uri(...)
        maven { url = uri("https://jitpack.io") } 
    }
}

rootProject.name = "esa-eear"
include(":app")
