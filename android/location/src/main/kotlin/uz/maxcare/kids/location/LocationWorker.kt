package uz.maxcare.kids.location

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import uz.maxcare.kids.core.events.SyncResult
import uz.maxcare.kids.core.location.LocationCollector
import uz.maxcare.kids.core.location.LocationSync

/** Периодический сбор позиции; интерфейс — для запуска из приложения без привязки к WorkManager. */
fun interface LocationScheduler {
    /** Поставить периодический сбор (идемпотентно: повторный вызов не плодит задачи). */
    fun schedulePeriodic()
}

/**
 * Раз в [INTERVAL_MINUTES] минут (минимум WorkManager) снимает позицию и отправляет накопленное.
 * Задача одна на приложение и переживает перезагрузку — WorkManager восстанавливает её сам.
 */
internal class WorkManagerLocationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationScheduler {
    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<LocationWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            // Сбор позиции сети не требует; ограничение — только чтобы отправка шла при связи.
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val WORK_NAME = "maxcare_location"
        const val INTERVAL_MINUTES = 15L
    }
}

internal class LocationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            LocationEntryPoint::class.java
        )
        entryPoint.locationCollector().collect()
        return when (entryPoint.locationSync().sync()) {
            SyncResult.DELIVERED, SyncResult.NOT_PAIRED -> Result.success()
            SyncResult.RETRY_LATER -> Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LocationEntryPoint {
        fun locationCollector(): LocationCollector
        fun locationSync(): LocationSync
    }
}
