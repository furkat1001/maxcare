package uz.maxcare.kids.reports

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.maxcare.kids.core.events.EventSync
import uz.maxcare.kids.core.events.SyncResult
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.network.OutgoingEvent
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString

/**
 * Отправляет очередь пачками по [BATCH_SIZE]. Событие удаляется из очереди только после того,
 * как сервер подтвердил приём; при ошибке всё остаётся до следующей попытки, а повторная
 * отправка безопасна — сервер узнаёт события по id.
 */
@Singleton
internal class EventUploader @Inject constructor(
    private val queue: SecureEventQueue,
    private val api: MaxCareApi,
    private val store: SecureStore
) : EventSync {
    private val mutex = Mutex()

    override suspend fun sync(): SyncResult = mutex.withLock {
        try {
            val token = store.readString(SecureKeys.DEVICE_TOKEN)
                ?: return@withLock SyncResult.NOT_PAIRED
            // Первая отправка — даже с пустой пачкой: это и есть отметка «телефон на связи».
            var batch = queue.peek(BATCH_SIZE)
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

    private suspend fun upload(token: String, batch: List<QueuedEvent>): SyncResult {
        val outgoing = batch.map { OutgoingEvent(it.id, it.type, it.occurredAt, it.detail) }
        return when (val response = api.uploadEvents(token, outgoing)) {
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
