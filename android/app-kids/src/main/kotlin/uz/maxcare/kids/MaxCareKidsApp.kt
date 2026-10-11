package uz.maxcare.kids

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.di.ApplicationScope
import uz.maxcare.kids.core.pairing.DevicePairing
import uz.maxcare.kids.core.protection.DeviceProtection
import uz.maxcare.kids.location.LocationScheduler

@HiltAndroidApp
class MaxCareKidsApp : Application() {
    @Inject
    lateinit var deviceProtection: DeviceProtection

    @Inject
    lateinit var devicePairing: DevicePairing

    @Inject
    lateinit var locationScheduler: LocationScheduler

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Повторное применение восстанавливает политики, если их сняли в обход приложения.
        appScope.launch { deviceProtection.applyPolicies() }
        // Периодический сбор геопозиции — только у привязанного телефона (есть согласие родителя).
        appScope.launch {
            if (devicePairing.isPaired()) locationScheduler.schedulePeriodic()
        }
    }
}
