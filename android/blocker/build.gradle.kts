plugins {
    alias(libs.plugins.maxcare.android.library)
}

android {
    namespace = "uz.maxcare.kids.blocker"
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
}
