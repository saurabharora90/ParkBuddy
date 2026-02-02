plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.foundry.base)
}

android {
  namespace = "dev.bongballe.parkbuddy.data.impl"
}

foundry {
  features {
    metro()
  }
}

dependencies {
  implementation(project(":core:base"))
  implementation(project(":core:data:api"))
  implementation(project(":core:model"))
  implementation(project(":core:theme"))
  implementation(project(":core:work-manager"))
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.datetime)
  implementation(libs.kotlinx.serialization.json)
}
