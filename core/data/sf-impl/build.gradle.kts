plugins {
  alias(libs.plugins.android.kmp.library)
  alias(libs.plugins.androidx.room)
  alias(libs.plugins.foundry.base)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

kotlin {
  android {
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
      implementation(project(":core:util"))
      implementation(libs.androidx.room.runtime)
      implementation(libs.androidx.room.sqlite.bundled)
      implementation(libs.kermit)
      implementation(libs.kotlinx.datetime)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.serialization.json.okio)
      implementation(libs.ktor.client.core)
    }
    commonTest.dependencies { implementation(kotlin("test")) }
    androidMain.dependencies {
      implementation(project(":core:data:impl"))
      implementation(libs.androidx.datastore.preferences)
    }
    iosMain.dependencies { implementation(project(":core:data:impl")) }
    getByName("androidHostTest").dependencies {
      implementation(project(":core:fakes"))
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

// Source .json files live in sf-data/ (module root). The generated park_buddy_db lives in
// commonMain/resources/sf-data/ so KMP bundles it automatically on Android. For iOS, we
// copy it to iosMain/resources/ so it ends up in the app bundle.
val copyPrebuiltDbToIos by
  tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("src/commonMain/resources/sf-data")) {
      include("park_buddy_db")
    }
    into(layout.projectDirectory.dir("src/iosMain/resources"))
  }

tasks.configureEach {
  if (name.startsWith("compileKotlinIos")) {
    dependsOn(copyPrebuiltDbToIos)
  }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
  add("kspAndroid", libs.androidx.room.compiler)
  add("kspIosArm64", libs.androidx.room.compiler)
  add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
