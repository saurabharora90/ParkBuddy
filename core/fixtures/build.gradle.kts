plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  jvm()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:model"))
      implementation(libs.androidx.annotation)
      implementation(libs.kotlinx.datetime)
    }
  }
}
