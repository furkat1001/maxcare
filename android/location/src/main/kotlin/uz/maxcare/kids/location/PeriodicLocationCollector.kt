package uz.maxcare.kids.location

import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import uz.maxcare.kids.core.location.LocationCollector
import uz.maxcare.kids.core.location.LocationProvider
import uz.maxcare.kids.core.storage.SecureStoreException

/**
 * Снимает текущую позицию и кладёт её в очередь. Отправку делает [LocationUploader] отдельно —
 * здесь только запись, чтобы отсутствие сети не мешало накоплению трека.
 * Перед фиксом [LocationStateMonitor] проверяет, не выключена ли геолокация: сбор вызывается
 * и из постоянной службы (раз в 5 мин), и из WorkManager, поэтому отдельный таймер не нужен.
 */
@Singleton
internal class PeriodicLocationCollector @Inject constructor(
    private val provider: LocationProvider,
    private val queue: SecureLocationQueue,
    private val clock: Clock,
    private val stateMonitor: LocationStateMonitor
) : LocationCollector {

    override suspend fun collect(): Boolean {
        stateMonitor.check()
        val fix = provider.current() ?: return false
        val location = QueuedLocation(
            id = UUID.randomUUID().toString(),
            latitude = fix.latitude,
            longitude = fix.longitude,
            accuracyMeters = fix.accuracyMeters,
            recordedAt = clock.instant().toString()
        )
        return try {
            queue.add(location)
            true
        } catch (ignored: SecureStoreException) {
            // Не записали — следующий фикс попробует снова; трек не защита, падать не из-за чего.
            false
        }
    }
}
