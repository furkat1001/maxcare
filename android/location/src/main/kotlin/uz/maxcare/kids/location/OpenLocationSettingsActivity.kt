package uz.maxcare.kids.location

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import uz.maxcare.kids.core.protection.TrustedSettingsWindow

/**
 * Без интерфейса: по нажатию на уведомление «Геолокация выключена» открывает окно доверия и
 * экран переключателя геолокации. На этом экране бывает название MaxCare Kids («Недавний доступ»),
 * и без окна защита от удаления закрыла бы его заглушкой.
 */
class OpenLocationSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            .trustedSettings()
            .open()
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        finish()
    }

    // Обычная Activity без AndroidX: @AndroidEntryPoint здесь недоступен.
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun trustedSettings(): TrustedSettingsWindow
    }
}
