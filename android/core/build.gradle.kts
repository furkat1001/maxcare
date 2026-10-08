plugins {
    alias(libs.plugins.maxcare.android.library)
    alias(libs.plugins.maxcare.hilt)
}

android {
    namespace = "uz.maxcare.kids.core"
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)

    // DataStore в unit-тестах работает на файловой системе в памяти:
    // на Windows настоящая запись через rename падает при втором сохранении.
    testImplementation(libs.okio.fakefilesystem)
}
