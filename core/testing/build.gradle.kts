plugins {
  kotlin("jvm")
}

dependencies {
  implementation(project(":core:base"))
  implementation(project(":core:model"))
  implementation(project(":core:data:api"))
  implementation(project(":core:bluetooth:api"))
  implementation(libs.kotlinx.coroutines.core)
}
