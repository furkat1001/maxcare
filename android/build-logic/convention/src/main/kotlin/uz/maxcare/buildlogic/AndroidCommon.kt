package uz.maxcare.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

internal fun CommonExtension.configureAndroidCommon() {
    compileSdk = AndroidConfig.COMPILE_SDK
    defaultConfig.minSdk = AndroidConfig.MIN_SDK
    defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    compileOptions.sourceCompatibility = AndroidConfig.JAVA_VERSION
    compileOptions.targetCompatibility = AndroidConfig.JAVA_VERSION
    lint.configureLint()
}

internal fun Lint.configureLint() {
    targetSdk = AndroidConfig.TARGET_SDK
    warningsAsErrors = true
    abortOnError = true
    // Обновления зависимостей отслеживаются отдельно, не ломаем сборку из-за новой версии библиотеки.
    disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
}

internal fun Project.addCommonDependencies() {
    dependencies {
        add("implementation", libs.lib("kotlinx-coroutines-android"))
        add("testImplementation", libs.lib("junit"))
        add("testImplementation", libs.lib("kotlinx-coroutines-test"))
        add("androidTestImplementation", libs.lib("androidx-test-ext-junit"))
        add("androidTestImplementation", libs.lib("androidx-test-runner"))
    }
}
