plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.metro)
}

kotlin {
  jvm()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:base"))
      implementation(project(":core:model"))
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.core)
    }
    commonTest.dependencies {
      implementation(project(":core:fakes"))
      implementation(libs.junit)
      implementation(libs.truth)
      implementation(libs.kotlinx.coroutines.test)
    }
  }
}
