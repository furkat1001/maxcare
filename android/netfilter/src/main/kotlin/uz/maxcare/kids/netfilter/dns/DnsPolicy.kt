package uz.maxcare.kids.netfilter.dns

/** Почему сайт заблокирован; в журнал родителя попадает только категория, без адреса. */
enum class BlockCategory {
    /** Заблокировал семейный DNS-провайдер: взрослый или вредоносный сайт (он их не различает). */
    FILTERED,

    /** Наш список: азартные игры и ставки — у провайдера такой категории нет. */
    GAMBLING
}

internal sealed interface DnsDecision {
    /** Отправить провайдеру как есть. */
    data object Forward : DnsDecision

    /** Ответить адресами [target], но под исходным именем: так работают SafeSearch и строгий YouTube. */
    data class Rewrite(val target: String) : DnsDecision

    /** Пустой ответ без ошибки: для записей HTTPS/SVCB подменённых имён. */
    data object NoData : DnsDecision

    data object NxDomain : DnsDecision

    data class Block(val category: BlockCategory) : DnsDecision
}

/**
 * Правила фильтра. Адреса подмены — официальные механизмы самих сервисов:
 * Google SafeSearch, строгий режим YouTube, Bing, DuckDuckGo, семейный поиск Яндекса.
 */
internal class DnsPolicy(
    private val gamblingDomains: Set<String> = GamblingDomains.LIST,
    private val youtubeTarget: String = YOUTUBE_STRICT
) {
    fun decide(name: String, type: Int): DnsDecision {
        if (name == FIREFOX_DOH_CANARY) return DnsDecision.NxDomain
        val target = rewriteTarget(name)
        return when {
            target != null -> when (type) {
                DnsType.A, DnsType.AAAA -> DnsDecision.Rewrite(target)

                // В записи HTTPS бывают подсказки адресов: браузер ушёл бы на обычный Google.
                DnsType.HTTPS, DnsType.SVCB -> DnsDecision.NoData

                else -> DnsDecision.Forward
            }

            matchesAny(name, gamblingDomains) -> DnsDecision.Block(BlockCategory.GAMBLING)

            else -> DnsDecision.Forward
        }
    }

    private fun rewriteTarget(name: String): String? = when {
        name in YOUTUBE_NAMES -> youtubeTarget
        GOOGLE_SEARCH.matches(name) -> "forcesafesearch.google.com"
        name in BING_NAMES -> "strict.bing.com"
        name in DUCKDUCKGO_NAMES -> "safe.duckduckgo.com"
        YANDEX_SEARCH.matches(name) -> "familysearch.yandex.ru"
        else -> null
    }

    private fun matchesAny(name: String, domains: Set<String>): Boolean {
        var candidate = name
        while (true) {
            if (candidate in domains) return true
            val dot = candidate.indexOf('.')
            if (dot < 0) return false
            candidate = candidate.substring(dot + 1)
        }
    }

    companion object {
        const val YOUTUBE_STRICT = "restrict.youtube.com"
        const val YOUTUBE_MODERATE = "restrictmoderate.youtube.com"

        /** Firefox отключает свой DoH, если этот домен не существует, — и ходит через наш фильтр. */
        private const val FIREFOX_DOH_CANARY = "use-application-dns.net"

        private val YOUTUBE_NAMES = setOf(
            "www.youtube.com",
            "m.youtube.com",
            "youtubei.googleapis.com",
            "youtube.googleapis.com",
            "www.youtube-nocookie.com"
        )

        // google.com, www.google.co.uz, google.ru и другие национальные домены поиска —
        // но не mail.google.com и прочие сервисы.
        private val GOOGLE_SEARCH = Regex("^(www\\.)?google\\.(com|[a-z]{2,3}|com?\\.[a-z]{2})$")

        private val BING_NAMES = setOf("bing.com", "www.bing.com")
        private val DUCKDUCKGO_NAMES =
            setOf("duckduckgo.com", "www.duckduckgo.com", "start.duckduckgo.com")
        private val YANDEX_SEARCH =
            Regex("^(www\\.)?(yandex\\.(ru|uz|kz|by|com|com\\.tr)|ya\\.ru)$")
    }
}
