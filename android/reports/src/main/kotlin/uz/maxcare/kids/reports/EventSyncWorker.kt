package uz.maxcare.kids.reports

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import uz.maxcare.kids.core.events.EventSync
import uz.maxcare.kids.core.events.SyncResult

/** Отложенная отправка; интерфейс — для тестов без WorkManager. */
internal fun interface EventSyncScheduler {
    fun scheduleWhenOnline()
}

/**
 * Если событие не удалось отправить сразу, Android отправит его сам, когда появится сеть, —
 * даже если постоянная служба уже остановлена (например, администратора отключили в режиме
 * полёта). Задача одна на все события: повторный вызов не плодит копии.
 */
internal class WorkManagerEventSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : EventSyncScheduler {
    override fun scheduleWhenOnline() {
        val request = OneTimeWorkRequestBuilder<EventSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(
            context
        ).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val WORK_NAME = "maxcare_event_sync"
        const val BACKOFF_SECONDS = 60L
    }
}

/**
 * Зависимости берёт через Hilt EntryPoint, а не через hilt-work: так не нужна ещё одна
 * библиотека и своя фабрика WorkManager.
 */
internal class EventSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sync = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncEntryPoint::class.java
        )
            .eventSync()
        return when (sync.sync()) {
            SyncResult.DELIVERED, SyncResult.NOT_PAIRED -> Result.success()
            SyncResult.RETRY_LATER -> Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncEntryPoint {
        fun eventSync(): EventSync
    }
}
