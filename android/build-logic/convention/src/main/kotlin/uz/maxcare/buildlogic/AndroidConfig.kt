package uz.maxcare.buildlogic

import org.gradle.api.JavaVersion

/** Общие версии платформы. targetSdk 36 — требование Google Play с 31.08.2026; compileSdk выше — его требуют свежие AndroidX и он не меняет поведение в рантайме. */
internal object AndroidConfig {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
    val JAVA_VERSION = JavaVersion.VERSION_17
}
