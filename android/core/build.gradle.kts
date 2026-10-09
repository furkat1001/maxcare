plugins {
    alias(libs.plugins.maxcare.android.library)
    alias(libs.plugins.maxcare.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "uz.maxcare.kids.core"

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Локальный сервер из `backend/`: 10.0.2.2 — компьютер, если смотреть из эмулятора.
            // Телефон по USB: `adb reverse tcp:8080 tcp:8080` и
            // ./gradlew ... -Pmaxcare.debugApiUrl=http://localhost:8080/
            // HTTP debug-сборке разрешён только к этим двум адресам (network_security_config).
            val debugApiUrl = providers.gradleProperty("maxcare.debugApiUrl")
                .getOrElse("http://10.0.2.2:8080/")
            buildConfigField("String", "API_BASE_URL", "\"$debugApiUrl\"")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"https://api.maxcare.dexx.uz/\"")
        }
    }
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // DataStore в unit-тестах работает на файловой системе в памяти:
    // на Windows настоящая запись через rename падает при втором сохранении.
    testImplementation(libs.okio.fakefilesystem)
    testImplementation(libs.okhttp.mockwebserver)
}
