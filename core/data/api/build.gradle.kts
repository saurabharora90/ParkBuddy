plugins {
  kotlin("jvm")
  alias(libs.plugins.metro)
}

dependencies {
  implementation(project(":core:base"))
  implementation(project(":core:model"))
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)

  testImplementation(project(":core:testing"))
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.kotlinx.coroutines.test)
}
