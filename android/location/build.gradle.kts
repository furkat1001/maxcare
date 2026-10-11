plugins {
    alias(libs.plugins.maxcare.android.library)
    alias(libs.plugins.maxcare.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "uz.maxcare.kids.location"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work)
}
