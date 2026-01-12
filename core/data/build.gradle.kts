plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.foundry.base)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.bongballe.parkbuddy.data"
}

foundry {
    features {
        metro()
    }
}

dependencies {
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
}
