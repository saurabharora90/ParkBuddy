plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.foundry.base)
}

android {
  namespace = "dev.bongballe.parkbuddy.theme"
}

foundry {
  features {
    compose()
  }
}
dependencies {
  implementation(platform(libs.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
}
