plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.parkbuddy.feature.map"
}

foundry {
  features {
    compose()
    metro()
  }
}

dependencies {
  implementation(platform(libs.compose.bom))
  implementation(project(":core:base"))
  implementation(project(":core:data:api"))
  implementation(project(":core:model"))
  implementation(project(":core:shared-ui"))
  implementation(project(":core:theme"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.bundles.compose)
  implementation(libs.bundles.compose.debug)
  implementation(libs.compose.material.icons)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.kotlinx.datetime)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.maps.compose)
  implementation(libs.metrox.viewmodel)
  implementation(libs.metrox.viewmodel.compose)
  implementation(libs.play.services.location)
}
