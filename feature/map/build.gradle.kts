plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  androidLibrary { namespace = "com.parkbuddy.feature.map" }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:base"))
      implementation(project(":core:data:api"))
      implementation(project(":core:model"))
      implementation(project(":core:shared-ui"))
      implementation(project(":core:theme"))
      implementation(libs.androidx.annotation)
      implementation(libs.compose.animation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.metrox.viewmodel)
      implementation(libs.metrox.viewmodel.compose)
    }
    androidMain.dependencies {
      implementation(libs.androidx.activity.compose)
      implementation(libs.kotlinx.coroutines.play.services)
      implementation(libs.maps.compose)
      implementation(libs.play.services.location)
    }
  }
}

foundry {
  features {
    compose(multiplatform = true)
    metro()
  }
}
