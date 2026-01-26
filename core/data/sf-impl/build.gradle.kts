plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.foundry.base)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.bongballe.parkbuddy.data.sf"
}

foundry {
    features {
        metro()
    }
}

dependencies {
    implementation(project(":core:base"))
    implementation(project(":core:data:api"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.sqlite.bundled)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.adapters.result)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    ksp(libs.androidx.room.compiler)
}
