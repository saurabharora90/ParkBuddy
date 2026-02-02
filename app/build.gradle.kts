plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.foundry.apk.versioning)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

android {
  namespace = "dev.bongballe.parkbuddy"

  defaultConfig {
    applicationId = "dev.bongballe.parkbuddy"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    buildConfig = true
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
  implementation(platform(libs.firebase.bom))
  implementation(project(":core:base"))
  implementation(project(":core:bluetooth"))
  implementation(project(":core:data:api"))
  implementation(project(":core:data:impl"))
  implementation(project(":core:data:sf-impl"))
  implementation(project(":core:model"))
  implementation(project(":core:network"))
  implementation(project(":core:theme"))
  implementation(project(":core:work-manager"))
  implementation(project(":feature:map"))
  implementation(project(":feature:onboarding"))
  implementation(project(":feature:reminders"))
  implementation(project(":feature:settings"))
  implementation("com.google.android.material:material:1.9.0")
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.bundles.compose)
  implementation(libs.bundles.compose.debug)
  implementation(libs.compose.material.icons)
  implementation(libs.compose.material.icons.extended)
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.lottie.compose)
  implementation(libs.metrox.android)
  implementation(libs.metrox.viewmodel)
  implementation(libs.metrox.viewmodel.compose)
  implementation(libs.workmanager)

  testImplementation(libs.junit)

  androidTestImplementation(libs.androidx.junit)
}
