plugins {
    alias(libs.plugins.maxcare.android.library)
    alias(libs.plugins.maxcare.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "uz.maxcare.kids.reports"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.serialization.json)
}
