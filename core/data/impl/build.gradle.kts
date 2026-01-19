plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.foundry.base)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.bongballe.parkbuddy.data.impl"
}

foundry {
    features {
        metro()
    }
}

dependencies {
    implementation(project(":core:data:api"))
    implementation(project(":core:database"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
}
