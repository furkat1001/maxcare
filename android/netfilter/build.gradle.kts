plugins {
    alias(libs.plugins.maxcare.android.library)
}

android {
    namespace = "uz.maxcare.kids.netfilter"
}

dependencies {
    implementation(project(":core"))
}
