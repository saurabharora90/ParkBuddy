plugins {
  kotlin("jvm")
  alias(libs.plugins.metro)
}

dependencies {
  implementation(project(":core:base"))
  implementation(project(":core:model"))
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
}
