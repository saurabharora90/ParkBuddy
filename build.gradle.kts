// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.kmp.library) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.detekt) apply true
  alias(libs.plugins.foundry.root) apply true
  alias(libs.plugins.foundry.base) apply true
  alias(libs.plugins.foundry.apk.versioning) apply true
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.firebase.crashlytics) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.multiplatform) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ktfmt) apply false
  alias(libs.plugins.androidx.room) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.metro) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.sort.dependencies) apply true
}

subprojects {
  apply(plugin = "com.ncorti.ktfmt.gradle")
  configure<com.ncorti.ktfmt.gradle.KtfmtExtension> { googleStyle() }

  plugins.withId("io.gitlab.arturbosch.detekt") {
    dependencies { detektPlugins(libs.detekt.compose.rules) }
  }
}
