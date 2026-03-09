plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
}

kotlin {
  androidLibrary {
    namespace = "dev.bongballe.parkbuddy.data.impl"
    withHostTestBuilder {}
  }

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:base"))
      implementation(project(":core:data:api"))
      implementation(project(":core:model"))
      api(libs.androidx.datastore.preferences)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)
    }
    androidMain.dependencies {
      implementation(project(":core:theme"))
      implementation(project(":core:work-manager"))
      implementation(libs.accompanist.permissions)
      implementation(libs.kotlinx.coroutines.play.services)
      implementation(libs.play.services.location)
    }
    getByName("androidHostTest").dependencies {
      implementation(project(":core:testing"))
      implementation(libs.androidx.junit)
      implementation(libs.androidx.test.core)
      implementation(libs.junit)
      implementation(libs.kotlinx.coroutines.test)
      implementation(libs.robolectric)
      implementation(libs.truth)
      implementation(libs.turbine)
    }
  }
}

foundry { features { metro() } }
