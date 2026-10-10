package uz.maxcare.kids.reports

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/** @param occurredAt время по часам телефона, ISO-8601. */
@Serializable
internal data class QueuedEvent(
    val id: String,
    val type: String,
    val occurredAt: String,
    // По умолчанию null: очередь, записанная прошлой версией, читается без ошибок.
    val detail: String? = null
)

/**
 * Очередь событий до отправки на сервер, в зашифрованном хранилище (CLAUDE.md: локальные данные
 * шифруются). Не больше [MAX_EVENTS]: при переполнении теряются самые старые — телефон, который
 * неделями без сети, не должен раздувать хранилище.
 */
@Singleton
internal class SecureEventQueue @Inject constructor(private val store: SecureStore) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /** @throws SecureStoreException если записать не удалось. */
    suspend fun add(event: QueuedEvent) = mutex.withLock {
        save((load() + event).takeLast(MAX_EVENTS))
    }

    suspend fun peek(limit: Int): List<QueuedEvent> = mutex.withLock { load().take(limit) }

    /** @throws SecureStoreException если записать не удалось. */
    suspend fun remove(ids: Set<String>) = mutex.withLock {
        save(load().filterNot { it.id in ids })
    }

    /**
     * Повреждённая очередь читается как пустая и перезаписывается при следующем событии.
     * Потеря нескольких событий хуже не станет от того, что приложение упадёт; а защиту
     * (пароль, блокировку) эти данные не затрагивают.
     */
    private suspend fun load(): List<QueuedEvent> = try {
        store.readString(KEY)?.let { json.decodeFromString<List<QueuedEvent>>(it) }.orEmpty()
    } catch (ignored: SecureStoreException) {
        emptyList()
    } catch (ignored: IllegalArgumentException) {
        // SerializationException — подкласс IllegalArgumentException.
        emptyList()
    }

    private suspend fun save(events: List<QueuedEvent>) {
        store.writeString(KEY, json.encodeToString(events))
    }

    companion object {
        const val MAX_EVENTS = 500
        private const val KEY = "event_queue"
    }
}
