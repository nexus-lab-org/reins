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

rootProject.name = "reins"

include(":app")
include(":presentation")
include(":domain")
include(":data")
include(":mosh")
include(":whisper")
include(":terminal-emulator")
include(":terminal-view")
