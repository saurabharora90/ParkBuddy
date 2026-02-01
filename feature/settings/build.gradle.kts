plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.parkbuddy.feature.settings"
}

foundry {
  features {
    metro()
    compose()
  }
}

dependencies {
  implementation(platform(libs.compose.bom))
  implementation(project(":core:base"))
  implementation(project(":core:bluetooth"))
  implementation(project(":core:data:api"))
  implementation(project(":core:model"))
  implementation(project(":core:shared-ui"))
  implementation(project(":core:theme"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.bundles.compose)
  implementation(libs.bundles.compose.debug)
  implementation(libs.compose.material.icons)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.metrox.android)
  implementation(libs.metrox.viewmodel)
  implementation(libs.metrox.viewmodel.compose)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
