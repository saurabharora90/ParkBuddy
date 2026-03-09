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
      implementation(compose.material3)
      implementation(compose.components.resources)
    }
    androidMain.dependencies { implementation(libs.androidx.core.ktx) }
  }
}

compose.resources { packageOfResClass = "dev.bongballe.parkbuddy.theme.resources" }

foundry { features { compose(multiplatform = true) } }
