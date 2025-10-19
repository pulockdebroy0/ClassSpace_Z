pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io") //
        // maven("https://maven.pkg.github.com/livekit/client-sdk-android") { /* creds */ }
    }
}

dependencyResolutionManagement {

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") //
        // maven("https://maven.pkg.github.com/livekit/client-sdk-android") { /* creds */ }
    }
}

rootProject.name = "ClassSpaceZ"
include(":app")
