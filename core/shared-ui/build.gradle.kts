plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  androidLibrary {
    namespace = "dev.parkbuddy.core.ui"
    androidResources { enable = true }
  }
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(project(":core:theme"))
      implementation(libs.compose.components.resources)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.ui.tooling.preview)
    }
  }
}

compose.resources { packageOfResClass = "dev.parkbuddy.core.ui.resources" }

foundry { features { compose(multiplatform = true) } }
