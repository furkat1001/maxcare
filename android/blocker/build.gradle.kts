plugins {
    alias(libs.plugins.maxcare.android.library)
}

android {
    namespace = "uz.maxcare.kids.blocker"
}

dependencies {
    implementation(project(":core"))
}
