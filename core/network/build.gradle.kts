plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.metro)
}

kotlin {
  jvm()

  sourceSets {
    commonMain.dependencies {
      api(project(":core:base"))
      api(libs.kotlinx.serialization.json)
      api(libs.ktor.client.core)

      implementation(libs.ktor.client.content.negotiation)
      implementation(libs.ktor.serialization.kotlinx.json)
    }
    jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
  }
}
