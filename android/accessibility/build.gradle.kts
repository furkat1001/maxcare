plugins {
    alias(libs.plugins.maxcare.android.library)
    alias(libs.plugins.maxcare.hilt)
}

android {
    namespace = "uz.maxcare.kids.accessibility"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":blocker"))
    implementation(libs.androidx.core.ktx)
}
