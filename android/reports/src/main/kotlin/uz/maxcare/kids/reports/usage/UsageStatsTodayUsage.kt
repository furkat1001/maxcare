package uz.maxcare.kids.reports.usage

import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import uz.maxcare.kids.core.di.IoDispatcher
import uz.maxcare.kids.core.limits.TodayUsage

/**
 * Сегодняшнее время по системной истории — опора для дневного лимита: её не сбросить ни
 * перезапуском MaxCare Kids, ни очисткой его данных. Считается тем же кодом, что и отчёт родителю.
 */
internal class UsageStatsTodayUsage @Inject constructor(
    private val history: UsageHistory,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : TodayUsage {

    override suspend fun foregroundToday(): Map<String, Long> {
        if (!history.hasAccess()) return emptyMap()
        return withContext(ioDispatcher) { today(ZoneId.systemDefault()) }
    }

    internal fun today(zone: ZoneId): Map<String, Long> {
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        val from = today.atStartOfDay(zone).toInstant()
        return foregroundTimeByDay(history.events(from, now), from, now, zone)[today].orEmpty()
    }
}
