plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.foundry.base)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.parkbuddy.feature.map"
}

foundry {
    features {
        compose()
        metro()
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))

    implementation(libs.maps.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.compose.debug)

    implementation(libs.metrox.viewmodel)
    implementation(libs.metrox.viewmodel.compose)
}