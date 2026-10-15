package uz.maxcare.kids.reports.usage

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.maxcare.kids.core.di.IoDispatcher
import uz.maxcare.kids.core.events.SyncResult
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.network.OutgoingAppUsage
import uz.maxcare.kids.core.network.OutgoingDayUsage
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.usage.AppUsageSync

/**
 * Каждый раз заново считает время в приложениях за последние [DAYS] дня по системной истории и
 * отправляет снимок целиком. Сервер заменяет дни, поэтому повтор безопасен, а своя очередь не
 * нужна: история хранится системой, на диск MaxCare ничего не пишется.
 */
@Singleton
internal class AppUsageUploader @Inject constructor(
    private val history: UsageHistory,
    private val api: MaxCareApi,
    private val store: SecureStore,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AppUsageSync {
    private val mutex = Mutex()

    override suspend fun sync(): SyncResult = mutex.withLock {
        try {
            val token = store.readString(SecureKeys.DEVICE_TOKEN)
                ?: return@withLock SyncResult.NOT_PAIRED
            // Доступ не выдан или отозван — отправлять нечего; мастер настройки попросит снова.
            if (!history.hasAccess()) return@withLock SyncResult.DELIVERED
            val days = withContext(ioDispatcher) { snapshot(ZoneId.systemDefault()) }
            when (val response = api.uploadAppUsage(token, days)) {
                is ApiResult.Ok -> SyncResult.DELIVERED

                // Токен отозван (например, телефон привязали заново): повторы ничего не дадут.
                is ApiResult.HttpError -> response.toSyncResult()

                else -> SyncResult.RETRY_LATER
            }
        } catch (ignored: SecureStoreException) {
            SyncResult.RETRY_LATER
        }
    }

    private fun snapshot(zone: ZoneId): List<OutgoingDayUsage> {
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val from = today.minusDays(DAYS - 1).atStartOfDay(zone).toInstant()
        return usageDays(history.events(from, now), history.launchableApps(), from, now, zone)
    }

    private fun ApiResult.HttpError.toSyncResult() =
        if (status == HTTP_UNAUTHORIZED) SyncResult.NOT_PAIRED else SyncResult.RETRY_LATER

    private companion object {
        const val DAYS = 3L
        const val HTTP_UNAUTHORIZED = 401
    }
}

/**
 * Снимок по дням для отправки: каждый день окна, даже пустой (пустой день — «телефоном не
 * пользовались», а не «нет данных»). В день попадают только приложения со значком, от [MIN_MS],
 * по убыванию времени.
 */
internal fun usageDays(
    events: List<ScreenEvent>,
    apps: Map<String, String>,
    from: Instant,
    to: Instant,
    zone: ZoneId
): List<OutgoingDayUsage> {
    val byDay = foregroundTimeByDay(events, from, to, zone)
    val firstDay = from.atZone(zone).toLocalDate()
    val lastDay = to.atZone(zone).toLocalDate()
    return generateSequence(firstDay) { it.plusDays(1) }
        .takeWhile { !it.isAfter(lastDay) }
        .map { day -> OutgoingDayUsage(day.toString(), appsOf(byDay[day].orEmpty(), apps)) }
        .toList()
}

private fun appsOf(millis: Map<String, Long>, apps: Map<String, String>): List<OutgoingAppUsage> =
    millis.filter { (pkg, ms) -> pkg in apps && ms >= MIN_MS }
        .entries
        .sortedByDescending { it.value }
        .take(MAX_APPS)
        .map { (pkg, ms) ->
            val label = apps.getValue(pkg).trim().ifEmpty { pkg }.take(MAX_LABEL)
            OutgoingAppUsage(pkg, label, ms)
        }

/** Меньше минуты — случайное касание или переход между приложениями, родителю это шум. */
internal const val MIN_MS = 60_000L

// Пределы сервера (docs/backend-api.md).
private const val MAX_APPS = 300
private const val MAX_LABEL = 100
