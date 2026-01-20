// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.detekt) apply true
    alias(libs.plugins.foundry.root) apply true
    alias(libs.plugins.foundry.base) apply true
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization ) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.sort.dependencies) apply true
    alias(libs.plugins.spotless) apply true
}

subprojects {
  plugins.withId("io.gitlab.arturbosch.detekt") {
    dependencies {
      // use your version
      detektPlugins(libs.detekt.compose.rules)
    }
  }
}
