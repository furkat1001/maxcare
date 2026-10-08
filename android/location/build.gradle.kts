plugins {
    alias(libs.plugins.maxcare.android.library)
}

android {
    namespace = "uz.maxcare.kids.location"
}

dependencies {
    implementation(project(":core"))
}
