pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "OpenCodeStudio"
include(":app")
include(":proot-engine")
project(":proot-engine").projectDir = file("runtime-upstream/android/proot-engine")
