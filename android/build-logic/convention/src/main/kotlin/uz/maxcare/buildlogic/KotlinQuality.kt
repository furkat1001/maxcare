package uz.maxcare.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/** detekt и ktlint для каждого модуля, чтобы `./gradlew detekt ktlintCheck` проверял весь проект. */
internal fun Project.configureKotlinQuality() {
    pluginManager.apply("dev.detekt")
    pluginManager.apply("org.jlleitschuh.gradle.ktlint")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel.set(true)
    }
    extensions.configure<KtlintExtension> {
        version.set(libs.version("ktlint"))
        android.set(true)
    }
}
