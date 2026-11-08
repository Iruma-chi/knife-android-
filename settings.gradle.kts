pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    maven { url = uri("https://maven-central-asia.storage-download.googleapis.com/maven2") }
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    maven { url = uri("https://maven-central-asia.storage-download.googleapis.com/maven2") }
    mavenCentral()
  }
}

rootProject.name = "Tsukihime Android 0.1"

include(":app")
