plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.metro)
  // TODO: Re-enable SKIE when it supports Kotlin 2.3.20.
  //  We need 2.3.20 for Metro's native cross-module DI aggregation (FIR hint generation).
  //  Without SKIE, suspend functions use completion-handler syntax in Swift instead of async/await.
  // alias(libs.plugins.skie)
}

kotlin {
  androidLibrary {
    namespace = "dev.parkbuddy.composeapp"
    androidResources { enable = true }
  }
  iosArm64()
  iosSimulatorArm64()

  listOf(targets.getByName("iosArm64"), targets.getByName("iosSimulatorArm64")).forEach {
    (it as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget).binaries.framework {
      baseName = "ParkBuddyShared"
      isStatic = true
      export(project(":core:model"))
      export(project(":core:base"))
      export(project(":core:data:api"))
      export(project(":feature:settings"))
      export(project(":feature:map"))
      export(project(":feature:reminders"))
      export(project(":feature:onboarding"))
    }
  }

  sourceSets {
    commonMain.dependencies {
      api(project(":core:model"))
      api(project(":core:base"))
      api(project(":core:navigation"))
      api(project(":core:data:api"))
      api(project(":core:data:impl"))
      api(project(":core:data:sf-impl"))
      api(project(":core:network"))
      api(project(":core:theme"))
      api(project(":core:shared-ui"))
      api(project(":feature:settings"))
      api(project(":feature:map"))
      api(project(":feature:reminders"))
      api(project(":feature:onboarding"))
      implementation(libs.compose.components.resources)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compottie)
      implementation(libs.compottie.resources)
      implementation(libs.metrox.viewmodel)
      implementation(libs.metrox.viewmodel.compose)
    }
    androidMain.dependencies {
      implementation(libs.androidx.lifecycle.viewmodel.navigation3)
      implementation(project(":core:bluetooth:api"))
      implementation(project(":core:bluetooth:impl"))
    }
  }
}

compose.resources { packageOfResClass = "dev.parkbuddy.composeapp.resources" }

foundry {
  features {
    compose(multiplatform = true)
    metro()
  }
}
