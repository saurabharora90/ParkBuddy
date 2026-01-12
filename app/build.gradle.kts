plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "dev.bongballe.parkbuddy"

  defaultConfig {
    applicationId = "dev.bongballe.parkbuddy"
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro",
      )
    }
  }
}

foundry {
  features {
    compose()
    metro()
  }
}

dependencies {
  implementation(platform(libs.compose.bom))
  implementation(project(":core:base"))
  implementation(project(":core:network"))
  implementation(project(":core:data"))
  implementation(project(":core:model"))
  implementation(project(":feature:map"))
  implementation(project(":feature:reminders"))
  // WorkManager
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.bundles.compose)
  implementation(libs.bundles.compose.debug)
  implementation(libs.metrox.android)
  implementation(libs.metrox.viewmodel)
  implementation(libs.metrox.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)

  testImplementation(libs.junit)

  androidTestImplementation(libs.androidx.junit)
}
