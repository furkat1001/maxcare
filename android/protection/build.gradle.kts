plugins {
    alias(libs.plugins.maxcare.android.library)
}

android {
    namespace = "uz.maxcare.kids.protection"
}

dependencies {
    implementation(project(":core"))
}
