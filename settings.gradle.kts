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
        // v3.8: PrismalAGSL 液态玻璃（MIT；GitHub Releases 经 JitPack 分发）
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "a41probe"
include(":app")
include(":design-tokens")
include(":design-system")
