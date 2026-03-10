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
  alias(libs.plugins.compose.multiplatform) apply false
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

  val catalog = rootProject.extensions.getByType<VersionCatalogsExtension>().named("libs")

  // Compose Multiplatform preview tooling for KMP Android library modules (AGP 9.0+).
  // Uses androidRuntimeClasspath instead of debugImplementation per:
  // https://kotlinlang.org/docs/multiplatform/compose-previews.html
  pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
    afterEvaluate {
      dependencies { "androidRuntimeClasspath"(catalog.findLibrary("compose-ui-tooling").get()) }
    }
  }

  val applyDetekt = {
    apply(plugin = "dev.detekt")
    configure<dev.detekt.gradle.extensions.DetektExtension> {
      buildUponDefaultConfig.set(true)
      enableCompilerPlugin.set(true)
      config.from(rootProject.file("config/detekt/detekt.yml"))
    }
    dependencies { detektPlugins(catalog.findLibrary("detekt-compose-rules").get()) }
  }
  pluginManager.withPlugin("org.jetbrains.kotlin.android") { applyDetekt() }
  pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") { applyDetekt() }
}

// Aggregate detekt task with type resolution for CI.
// Foundry's globalCiDetekt doesn't wire up detekt 2.0 tasks, so we collect them manually.
val ciDetekt =
  tasks.register("ciDetekt") {
    description = "Runs all detekt tasks with type resolution"
    group = "verification"
  }
subprojects {
  plugins.withId("dev.detekt") {
    afterEvaluate {
      tasks.names
        .filter { it.startsWith("detektMain") }
        .forEach { taskName -> ciDetekt.configure { dependsOn("${this@subprojects.path}:$taskName") } }
    }
  }
}

// Aggregate task for KMP Android host tests (modules using com.android.kotlin.multiplatform.library).
// Foundry's globalCiUnitTest doesn't pick these up due to a bug with the KMP plugin.
// Only wires modules that actually have androidHostTest sources (afterEvaluate so the task exists).
val globalCiAndroidHostTest =
  tasks.register("globalCiAndroidHostTest") {
    description = "Runs testAndroidHostTest for all KMP Android library modules"
    group = "verification"
  }
subprojects {
  plugins.withId("com.android.kotlin.multiplatform.library") {
    afterEvaluate {
      if ("testAndroidHostTest" in tasks.names) {
        globalCiAndroidHostTest.configure { dependsOn("${this@subprojects.path}:testAndroidHostTest") }
      }
    }
  }
}
