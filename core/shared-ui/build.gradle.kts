plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  androidLibrary { namespace = "dev.parkbuddy.core.ui" }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:theme"))
      implementation(compose.material3)
      implementation(compose.materialIconsExtended)
      implementation(compose.ui)
      implementation(compose.uiTooling)
    }
  }
}

foundry { features { compose(multiplatform = true) } }
