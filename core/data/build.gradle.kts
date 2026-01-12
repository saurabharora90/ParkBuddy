plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.foundry.base)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.parkbuddy.core.data"
}

foundry {
    features {
        metro()
    }
}


dependencies {
    implementation(project(":core:network"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)

    ksp(libs.androidx.room.compiler)
}
