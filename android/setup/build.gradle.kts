plugins {
    alias(libs.plugins.maxcare.android.library)
    alias(libs.plugins.maxcare.hilt)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "uz.maxcare.kids.setup"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
