plugins {
    alias(libs.plugins.maxcare.android.application)
    alias(libs.plugins.maxcare.hilt)
}

android {
    namespace = "uz.maxcare.kids"

    defaultConfig {
        applicationId = "uz.maxcare.kids"
        versionCode = 1
        versionName = "0.1.0"
    }

    androidResources {
        generateLocaleConfig = true
    }

    lint {
        checkDependencies = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":protection"))
    implementation(project(":accessibility"))
    implementation(project(":netfilter"))
    implementation(project(":ml"))
    implementation(project(":blocker"))
    implementation(project(":location"))
    implementation(project(":reports"))
    implementation(project(":setup"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity)
}
