plugins {
    alias(libs.plugins.maxcare.android.library)
}

android {
    namespace = "uz.maxcare.kids.reports"
}

dependencies {
    implementation(project(":core"))
}
