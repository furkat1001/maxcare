package uz.maxcare.kids.reports

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.maxcare.kids.core.events.EventSync
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.network.OutgoingEvent
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString

/**
 * Отправляет очередь пачками по [BATCH_SIZE]. Событие удаляется из очереди только после того,
 * как сервер подтвердил приём; при ошибке всё остаётся до следующей попытки (раз в 15 минут),
 * а повторная отправка безопасна — сервер узнаёт события по id.
 */
@Singleton
internal class EventUploader @Inject constructor(
    private val queue: SecureEventQueue,
    private val api: MaxCareApi,
    private val store: SecureStore
) : EventSync {
    private val mutex = Mutex()

    override suspend fun sync(): Boolean = mutex.withLock {
        try {
            val token = deviceToken() ?: return@withLock false
            // Первая отправка — даже с пустой пачкой: это и есть отметка «телефон на связи».
            var batch = queue.peek(BATCH_SIZE)
            var delivered = upload(token, batch)
            while (delivered && batch.size == BATCH_SIZE) {
                batch = queue.peek(BATCH_SIZE)
                delivered = batch.isEmpty() || upload(token, batch)
            }
            delivered
        } catch (ignored: SecureStoreException) {
            // Не смогли прочитать токен или убрать отправленное — попробуем в следующий раз.
            false
        }
    }

    private suspend fun upload(token: String, batch: List<QueuedEvent>): Boolean {
        val outgoing = batch.map { OutgoingEvent(it.id, it.type, it.occurredAt) }
        val delivered = api.uploadEvents(token, outgoing) is ApiResult.Ok
        if (delivered) queue.remove(batch.map { it.id }.toSet())
        return delivered
    }

    // Не привязан — отправлять некуда; события подождут привязки.
    private suspend fun deviceToken(): String? = store.readString(SecureKeys.DEVICE_TOKEN)

    private companion object {
        const val BATCH_SIZE = 100
    }
}
