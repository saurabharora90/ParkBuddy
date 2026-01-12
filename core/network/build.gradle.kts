plugins {
  kotlin("jvm")
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.metro)
}

dependencies {
  api(project(":core:base"))
  api(libs.kotlinx.serialization.json)

  implementation(libs.okhttp)
}
