plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  androidLibrary {
    namespace = "com.parkbuddy.feature.reminders"
    withHostTestBuilder {}
  }
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:base"))
      implementation(project(":core:data:api"))
      implementation(project(":core:model"))
      implementation(project(":core:shared-ui"))
      implementation(project(":core:theme"))
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.kotlinx.datetime)
      implementation(libs.metrox.viewmodel)
      implementation(libs.metrox.viewmodel.compose)
    }
    androidMain.dependencies {
      implementation(project(":core:work-manager"))
      implementation(libs.accompanist.permissions)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.core.ktx)
      implementation(libs.metrox.android)
    }
    getByName("androidHostTest").dependencies {
      implementation(project(":core:fakes"))
      implementation(libs.junit)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.robolectric)
      implementation(libs.truth)
      implementation(libs.turbine)
    }
  }
}

foundry {
  features {
    compose(multiplatform = true)
    metro()
  }
}
