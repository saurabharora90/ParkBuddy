plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.android)
}

android {
  namespace = "com.parkbuddy.feature.reminders"
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
  implementation(project(":core:data:api"))
  implementation(project(":core:model"))
  implementation(project(":core:shared-ui"))
  implementation(project(":core:theme"))
  implementation(project(":core:work-manager"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.bundles.compose)
  implementation(libs.bundles.compose.debug)
  implementation(libs.compose.material.icons)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.kotlinx.datetime)
  implementation(libs.metrox.android)
  implementation(libs.metrox.viewmodel)
  implementation(libs.metrox.viewmodel.compose)
  implementation(libs.play.services.location)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
