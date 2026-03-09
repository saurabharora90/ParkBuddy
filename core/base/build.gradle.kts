plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.metro)
}

kotlin {
  jvm()

  sourceSets { commonMain.dependencies { api(libs.kotlinx.coroutines.core) } }
}
