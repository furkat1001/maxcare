package uz.maxcare.kids.netfilter

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.maxcare.kids.core.di.ApplicationScope
import uz.maxcare.kids.core.events.EventRecorder
import uz.maxcare.kids.core.events.EventType
import uz.maxcare.kids.netfilter.dns.BlockCategory

internal fun interface ElapsedClock {
    /** Миллисекунды с включения телефона: перевод часов не сбивает лимиты. */
    fun now(): Long
}

internal fun interface BlockNotifier {
    fun showSiteBlocked()
}

/**
 * Что делать при блокировке сайта. Одна страница тянет десятки запросов, поэтому:
 * - ребёнку — уведомление не чаще раза в минуту;
 * - родителю — событие с категорией и адресом, один и тот же адрес не чаще раза в час,
 *   и не больше [MAX_DOMAINS_PER_HOUR] разных адресов в час — чтобы журнал нельзя было засыпать.
 *
 * Адрес записывается только для заблокированных сайтов: их пометил фильтр (CLAUDE.md).
 */
@Singleton
internal class BlockReporter @Inject constructor(
    private val events: EventRecorder,
    private val notifier: BlockNotifier,
    private val clock: ElapsedClock,
    @ApplicationScope private val appScope: CoroutineScope
) {
    private val lock = Any()
    private var lastNotification: Long? = null
    private val recentlyRecorded = mutableMapOf<Pair<BlockCategory, String>, Long>()

    fun onBlocked(category: BlockCategory, domain: String) {
        val now = clock.now()
        val (notify, record) = synchronized(lock) {
            val notify = lastNotification.let { it == null || now - it >= NOTIFICATION_INTERVAL_MS }
            if (notify) lastNotification = now
            notify to shouldRecord(category to domain, now)
        }
        if (notify) notifier.showSiteBlocked()
        if (record) {
            appScope.launch {
                events.record(category.toEventType(), domain.takeIf(::isPlainDomain))
            }
        }
    }

    private fun shouldRecord(key: Pair<BlockCategory, String>, now: Long): Boolean {
        recentlyRecorded.entries.removeAll { now - it.value >= EVENT_INTERVAL_MS }
        val record = key !in recentlyRecorded && recentlyRecorded.size < MAX_DOMAINS_PER_HOUR
        if (record) recentlyRecorded[key] = now
        return record
    }

    // В DNS-имени технически может быть что угодно; родителю уходит только обычное доменное имя.
    private fun isPlainDomain(domain: String) =
        domain.length <= MAX_DOMAIN_LENGTH && DOMAIN.matches(domain)

    private fun BlockCategory.toEventType() = when (this) {
        BlockCategory.FILTERED -> EventType.SITE_BLOCKED_FILTERED
        BlockCategory.GAMBLING -> EventType.SITE_BLOCKED_GAMBLING
    }

    private companion object {
        const val NOTIFICATION_INTERVAL_MS = 60_000L
        const val EVENT_INTERVAL_MS = 3_600_000L
        const val MAX_DOMAINS_PER_HOUR = 100
        const val MAX_DOMAIN_LENGTH = 253
        val DOMAIN = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+$")
    }
}
