package uz.maxcare.kids.location

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.maxcare.kids.core.events.SyncResult
import uz.maxcare.kids.core.location.LocationSync
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.network.OutgoingLocation
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString

/**
 * Отправляет очередь позиций пачками по [BATCH_SIZE]. Точка удаляется из очереди только после
 * подтверждения сервером; при ошибке всё остаётся до следующей попытки, а повтор безопасен —
 * сервер узнаёт точки по id.
 */
@Singleton
internal class LocationUploader @Inject constructor(
    private val queue: SecureLocationQueue,
    private val api: MaxCareApi,
    private val store: SecureStore
) : LocationSync {
    private val mutex = Mutex()

    override suspend fun sync(): SyncResult = mutex.withLock {
        try {
            val token = store.readString(SecureKeys.DEVICE_TOKEN)
                ?: return@withLock SyncResult.NOT_PAIRED
            var batch = queue.peek(BATCH_SIZE)
            // Нечего отправлять — это не ошибка.
            if (batch.isEmpty()) return@withLock SyncResult.DELIVERED
            var result = upload(token, batch)
            while (result == SyncResult.DELIVERED && batch.size == BATCH_SIZE) {
                batch = queue.peek(BATCH_SIZE)
                if (batch.isEmpty()) break
                result = upload(token, batch)
            }
            result
        } catch (ignored: SecureStoreException) {
            // Не смогли прочитать токен или убрать отправленное — попробуем в следующий раз.
            SyncResult.RETRY_LATER
        }
    }

    private suspend fun upload(token: String, batch: List<QueuedLocation>): SyncResult {
        val outgoing = batch.map {
            OutgoingLocation(it.id, it.latitude, it.longitude, it.accuracyMeters, it.recordedAt)
        }
        return when (val response = api.uploadLocations(token, outgoing)) {
            is ApiResult.Ok -> {
                queue.remove(batch.map { it.id }.toSet())
                SyncResult.DELIVERED
            }

            // Токен отозван (например, телефон привязали заново): повторы ничего не дадут.
            is ApiResult.HttpError -> response.toSyncResult()

            else -> SyncResult.RETRY_LATER
        }
    }

    private fun ApiResult.HttpError.toSyncResult() =
        if (status == HTTP_UNAUTHORIZED) SyncResult.NOT_PAIRED else SyncResult.RETRY_LATER

    private companion object {
        const val BATCH_SIZE = 100
        const val HTTP_UNAUTHORIZED = 401
    }
}
