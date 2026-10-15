package uz.maxcare.kids.reports.usage

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
import uz.maxcare.kids.core.usage.AppUsageScheduler
import uz.maxcare.kids.core.usage.AppUsageSync

/**
 * Раз в [INTERVAL_MINUTES] минут (минимум WorkManager) отправляет время в приложениях.
 * Задача одна на приложение и переживает перезагрузку — WorkManager восстанавливает её сам.
 */
internal class WorkManagerAppUsageScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : AppUsageScheduler {
    override fun schedulePeriodic() {
        val request = PeriodicWorkRequestBuilder<AppUsageWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
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
        const val WORK_NAME = "maxcare_app_usage"
        const val INTERVAL_MINUTES = 15L
    }
}

internal class AppUsageWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(
            applicationContext,
            AppUsageEntryPoint::class.java
        ).appUsageSync()
        return when (sync.sync()) {
            SyncResult.DELIVERED, SyncResult.NOT_PAIRED -> Result.success()
            SyncResult.RETRY_LATER -> Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AppUsageEntryPoint {
        fun appUsageSync(): AppUsageSync
    }
}
