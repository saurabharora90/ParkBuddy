plugins { alias(libs.plugins.kotlin.multiplatform) }

kotlin {
  jvm()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(project(":core:fixtures"))
      implementation(project(":core:base"))
      implementation(project(":core:model"))
      implementation(project(":core:data:api"))
      implementation(libs.kotlinx.coroutines.core)
    }
    jvmMain.dependencies { implementation(project(":core:bluetooth:api")) }
  }
}
