plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.foundry.base)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.bongballe.parkbuddy.database"
}

foundry {
    features {
        metro()
    }
}


dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.serialization.json)

    ksp(libs.androidx.room.compiler)
}
