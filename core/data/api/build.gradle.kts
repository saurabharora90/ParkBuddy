plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
}
