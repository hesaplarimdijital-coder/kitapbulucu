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
        // desktop-kit is built by JitPack from its git tags; nothing else comes from there
        maven("https://jitpack.io") {
            content { includeGroup("com.github.hesaplarimdijital-coder") }
        }
    }
}

rootProject.name = "kitapbulucu"
include(":app")
