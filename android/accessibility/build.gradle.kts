plugins {
    alias(libs.plugins.maxcare.android.library)
}

android {
    namespace = "uz.maxcare.kids.accessibility"
}

dependencies {
    implementation(project(":core"))
}
