plugins {
    `kotlin-dsl`
}

group = "uz.maxcare.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.maxcare.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.maxcare.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.maxcare.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
    }
}
