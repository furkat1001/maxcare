plugins {
    alias(libs.plugins.maxcare.android.library)
    alias(libs.plugins.maxcare.hilt)
}

android {
    namespace = "uz.maxcare.kids.blocker"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
}
