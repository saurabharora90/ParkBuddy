plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.androidx.room)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

kotlin {
  androidLibrary {
    namespace = "dev.bongballe.parkbuddy.data.sf"
    withHostTestBuilder {}
  }
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(project(":core:base"))
      implementation(project(":core:data:api"))
      implementation(project(":core:model"))
      implementation(project(":core:network"))
      implementation(libs.androidx.room.runtime)
      implementation(libs.androidx.room.sqlite.bundled)
      implementation(libs.kermit)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.ktor.client.core)
    }
    androidMain.dependencies { implementation(libs.androidx.datastore.preferences) }
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

room { schemaDirectory("$projectDir/schemas") }

dependencies {
  add("kspAndroid", libs.androidx.room.compiler)
  add("kspIosArm64", libs.androidx.room.compiler)
  add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
