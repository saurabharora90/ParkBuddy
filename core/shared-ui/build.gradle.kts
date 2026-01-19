plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.foundry.base)
}

android {
    namespace = "dev.parkbuddy.core.ui"
}

foundry {
  features {
    compose()
  }
}

dependencies {
  implementation(platform(libs.compose.bom))
  implementation(project(":core:theme"))
  implementation(libs.bundles.compose)
  implementation(libs.bundles.compose.debug)
  implementation(libs.compose.material.icons)
  implementation(libs.compose.material.icons.extended)
}
