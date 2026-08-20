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
        maven("https://jitpack.io")
        mavenLocal()
    }
}

rootProject.name = "lastchat"
include(":app")
include(":shared")
include(":iosApp")
include(":ui-core")
include(":highlight")
include(":ai")
include(":search")
include(":tts")
include(":speech")
include(":common")
include(":app:baselineprofile")
include(":document")
include(":workspace")
include(":local-llm")
