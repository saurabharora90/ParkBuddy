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

rootProject.name = "Park Buddy"
include(":app")
include(":core:model")
include(":core:base")
include(":core:bluetooth")
include(":core:network")
include(":core:theme")
include(":core:shared-ui")
include(":core:work-manager")
include(":core:data:api")
include(":core:data:impl")
include(":core:data:sf-impl")
include(":feature:map")
include(":feature:reminders")
include(":feature:onboarding")
include(":feature:settings")
