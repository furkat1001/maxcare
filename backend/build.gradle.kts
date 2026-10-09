plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    application
}

group = "uz.maxcare"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("uz.maxcare.backend.ApplicationKt")
}

dependencies {
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.lettuce)
    implementation(libs.bouncycastle)
    implementation(libs.logback)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.test {
    useJUnitPlatform()
}

// Только для локального запуска `./gradlew run`. В рабочем окружении ключ задаётся переменной
// MAXCARE_SECRET_KEY и не хранится в репозитории; без неё сервер не стартует.
tasks.named<JavaExec>("run") {
    environment("MAXCARE_SECRET_KEY", "ZGV2LW9ubHkta2V5LWRvLW5vdC11c2UtaW4tcHJvZCE=")
}

detekt {
    buildUponDefaultConfig.set(true)
}

ktlint {
    version.set(libs.versions.ktlint)
}
