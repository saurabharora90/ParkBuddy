plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.foundry.base)
}

android {
    namespace = "dev.bongballe.parkbuddy.core.bluetooth.impl"
}

foundry {
  features {
    metro()
  }
}

dependencies {
    implementation(project(":core:bluetooth:api"))
    implementation(project(":core:data:api"))
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.metrox.android)
}
