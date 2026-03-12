plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  androidLibrary { namespace = "com.parkbuddy.feature.settings" }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:base"))
      implementation(project(":core:bluetooth:api"))
      implementation(project(":core:data:api"))
      implementation(project(":core:model"))
      implementation(project(":core:navigation"))
      implementation(project(":core:shared-ui"))
      implementation(project(":core:theme"))
      implementation(libs.compose.material3)
      implementation(libs.compose.ui)
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.metrox.viewmodel)
      implementation(libs.metrox.viewmodel.compose)
    }
  }
}

foundry {
  features {
    compose(multiplatform = true)
    metro()
  }
}
