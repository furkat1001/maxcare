plugins {
    alias(libs.plugins.maxcare.android.library)
    alias(libs.plugins.maxcare.hilt)
}

android {
    namespace = "uz.maxcare.kids.ml"

    // Модель .tflite не сжимать — иначе её нельзя замапить в память.
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.litert.runtime)
    implementation(libs.litert.support)
}
