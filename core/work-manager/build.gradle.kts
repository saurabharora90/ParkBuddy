plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.foundry.base)
}

android {
  namespace = "dev.parkbuddy.core.workmanager"
}

foundry {
  features {
    metro()
  }
}

dependencies {
  api(libs.workmanager)

  implementation(libs.metrox.android)
}
