package uz.maxcare.kids.location

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

/** @param recordedAt время фикса по часам телефона, ISO-8601. */
@Serializable
internal data class QueuedLocation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    // По умолчанию null: очередь, записанная прошлой версией, читается без ошибок.
    val accuracyMeters: Double? = null,
    val recordedAt: String
)

/**
 * Очередь геопозиций до отправки, в зашифрованном хранилище (CLAUDE.md: локальные данные
 * шифруются). Не больше [MAX_LOCATIONS]: телефон, который неделями без сети, не должен
 * раздувать хранилище — при переполнении теряются самые старые точки.
 */
@Singleton
internal class SecureLocationQueue @Inject constructor(private val store: SecureStore) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /** @throws SecureStoreException если записать не удалось. */
    suspend fun add(location: QueuedLocation) = mutex.withLock {
        save((load() + location).takeLast(MAX_LOCATIONS))
    }

    suspend fun peek(limit: Int): List<QueuedLocation> = mutex.withLock { load().take(limit) }

    /** @throws SecureStoreException если записать не удалось. */
    suspend fun remove(ids: Set<String>) = mutex.withLock {
        save(load().filterNot { it.id in ids })
    }

    // Повреждённая очередь читается как пустая и перезаписывается при следующем фиксе: потеря
    // нескольких точек защиту (пароль, блокировку) не затрагивает.
    private suspend fun load(): List<QueuedLocation> = try {
        store.readString(KEY)?.let { json.decodeFromString<List<QueuedLocation>>(it) }.orEmpty()
    } catch (ignored: SecureStoreException) {
        emptyList()
    } catch (ignored: IllegalArgumentException) {
        // SerializationException — подкласс IllegalArgumentException.
        emptyList()
    }

    private suspend fun save(locations: List<QueuedLocation>) {
        store.writeString(KEY, json.encodeToString(locations))
    }

    companion object {
        const val MAX_LOCATIONS = 500
        private const val KEY = "location_queue"
    }
}
