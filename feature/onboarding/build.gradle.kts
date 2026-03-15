plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  androidLibrary { namespace = "com.parkbuddy.feature.onboarding" }
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:base"))
      implementation(project(":core:data:api"))
      implementation(project(":core:model"))
      implementation(project(":core:shared-ui"))
      implementation(project(":core:theme"))
      implementation(libs.compose.animation)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.metrox.viewmodel)
      implementation(libs.metrox.viewmodel.compose)
    }
    androidMain.dependencies {
      implementation(project(":core:bluetooth:api"))
      implementation(project(":core:navigation"))
      implementation(libs.accompanist.permissions)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.core.ktx)
    }
  }
}

foundry {
  features {
    compose(multiplatform = true)
    metro()
  }
}
