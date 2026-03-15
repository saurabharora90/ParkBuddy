plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.metro)
}

kotlin {
  jvm()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(libs.kotlinx.coroutines.core)
      api(libs.kermit)
    }
  }
}
