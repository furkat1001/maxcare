plugins {
    alias(libs.plugins.maxcare.android.library)
}

android {
    namespace = "uz.maxcare.kids.ml"
}

dependencies {
    implementation(project(":core"))
}
