plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.metro)
}

kotlin {
  jvm()

  sourceSets {
    commonMain.dependencies {
      api(libs.compose.navigation3.ui)
      api(libs.kotlinx.coroutines.core)
    }
  }
}

foundry {
  features {
    metro()
    compose(multiplatform = true)
  }
}
