plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.foundry.base)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.parkbuddy.feature.reminders"
}

foundry {
    features {
        metro()
        compose()
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(project(":core:base"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))
    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.compose.debug)
    implementation(libs.compose.material.icons)
    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}
