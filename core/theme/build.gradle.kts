plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  androidLibrary {
    namespace = "dev.bongballe.parkbuddy.theme"
    androidResources { enable = true }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.components.resources)
      implementation(libs.compose.material3)
    }
    androidMain.dependencies { implementation(libs.androidx.core.ktx) }
  }
}

compose.resources { packageOfResClass = "dev.bongballe.parkbuddy.theme.resources" }

foundry { features { compose(multiplatform = true) } }
