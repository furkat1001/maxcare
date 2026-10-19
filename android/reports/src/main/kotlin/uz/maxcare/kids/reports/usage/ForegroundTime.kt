package uz.maxcare.kids.reports.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Считает, сколько миллисекунд каждое приложение было на экране, по календарным дням [zone].
 *
 * На экране одновременно одно окно: новое [ScreenEvent.Resumed] закрывает предыдущее, а
 * [ScreenEvent.Paused] закрывает окно, только если это то же самое окно (окна одного приложения
 * могут меняться в любом порядке). Сессия через полночь делится между днями; незакрытая к [to]
 * сессия считается до [to]. Всё, что вне окна [from]–[to], отбрасывается.
 *
 * @return день → (пакет → миллисекунды); дни и приложения без времени не попадают.
 */
internal fun foregroundTimeByDay(
    events: List<ScreenEvent>,
    from: Instant,
    to: Instant,
    zone: ZoneId
): Map<LocalDate, Map<String, Long>> {
    val totals = mutableMapOf<LocalDate, MutableMap<String, Long>>()
    var open: ScreenEvent.Resumed? = null

    fun close(end: Instant) {
        val session = open ?: return
        open = null
        addSplitByDay(totals, session.packageName, maxOf(session.at, from), minOf(end, to), zone)
    }

    for (event in events.sortedBy { it.at }) {
        when (event) {
            is ScreenEvent.Resumed -> {
                close(event.at)
                open = event
            }

            is ScreenEvent.Paused -> {
                val current = open
                if (current != null &&
                    current.packageName == event.packageName &&
                    current.activity == event.activity
                ) {
                    close(event.at)
                }
            }

            is ScreenEvent.ScreenOff -> close(event.at)
        }
    }
    close(to)
    return totals
}

private fun addSplitByDay(
    totals: MutableMap<LocalDate, MutableMap<String, Long>>,
    packageName: String,
    start: Instant,
    end: Instant,
    zone: ZoneId
) {
    var cursor = start
    while (cursor < end) {
        val day = cursor.atZone(zone).toLocalDate()
        val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant()
        val sliceEnd = minOf(end, nextMidnight)
        val millis = sliceEnd.toEpochMilli() - cursor.toEpochMilli()
        val perApp = totals.getOrPut(day) { mutableMapOf() }
        perApp[packageName] = (perApp[packageName] ?: 0L) + millis
        cursor = sliceEnd
    }
}
