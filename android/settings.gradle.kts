pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "maxcare-android"

include(
    ":app-kids",
    ":core",
    ":protection",
    ":accessibility",
    ":netfilter",
    ":ml",
    ":blocker",
    ":location",
    ":reports",
    ":setup",
)
