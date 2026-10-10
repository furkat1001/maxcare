package uz.maxcare.kids.netfilter.dns

/** Куда уходят запросы, которые фильтр не решает сам; null — провайдер недоступен. */
internal fun interface DnsUpstream {
    suspend fun query(packet: ByteArray): ByteArray?
}

/**
 * Отвечает на один DNS-запрос телефона. Не бросает исключений: на повреждённый запрос — null
 * (пакет просто отбрасывается), на недоступного провайдера — SERVFAIL (приложение повторит).
 */
internal class DnsEngine(
    private val policy: DnsPolicy,
    private val upstream: DnsUpstream,
    private val onBlocked: (category: BlockCategory, domain: String) -> Unit
) {
    suspend fun handle(packet: ByteArray): ByteArray? {
        val query = DnsCodec.parseQuery(packet) ?: return null
        return when (val decision = policy.decide(query.name, query.type)) {
            DnsDecision.Forward -> forward(query, packet)

            is DnsDecision.Rewrite -> rewrite(query, decision.target)

            DnsDecision.NoData -> DnsCodec.buildResponse(query, DnsRcode.NOERROR, emptyList())

            DnsDecision.NxDomain -> DnsCodec.buildResponse(query, DnsRcode.NXDOMAIN, emptyList())

            is DnsDecision.Block -> {
                onBlocked(decision.category, query.name)
                blockedAnswer(query)
            }
        }
    }

    private suspend fun forward(query: DnsQuestion, packet: ByteArray): ByteArray {
        val response = upstream.query(packet) ?: return servfail(query)
        if (isBlockedByUpstream(response)) onBlocked(BlockCategory.FILTERED, query.name)
        return response
    }

    // Адреса целевого имени (например, forcesafesearch.google.com) под исходным именем.
    private suspend fun rewrite(query: DnsQuestion, target: String): ByteArray {
        val response = upstream.query(DnsCodec.buildQuery(query.id, target, query.type))
        // Провайдер недоступен или ответ повреждён — одинаково SERVFAIL.
        val records = response?.let(DnsCodec::parseAnswers) ?: return servfail(query)
        val addresses = records.filter { it.type == query.type }
        return DnsCodec.buildResponse(query, DnsRcode.NOERROR, addresses)
    }

    // Как у семейного провайдера: «нулевой» адрес, а не NXDOMAIN, — приложения не повторяют запрос.
    private fun blockedAnswer(query: DnsQuestion): ByteArray {
        val answer = when (query.type) {
            DnsType.A -> listOf(DnsRecord(DnsType.A, BLOCKED_TTL, ByteArray(IPV4_SIZE)))
            DnsType.AAAA -> listOf(DnsRecord(DnsType.AAAA, BLOCKED_TTL, ByteArray(IPV6_SIZE)))
            else -> emptyList()
        }
        return DnsCodec.buildResponse(query, DnsRcode.NOERROR, answer)
    }

    // Cloudflare Families отвечает на заблокированное 0.0.0.0 / ::.
    private fun isBlockedByUpstream(response: ByteArray): Boolean =
        DnsCodec.parseAnswers(response).orEmpty().any { record ->
            (record.type == DnsType.A || record.type == DnsType.AAAA) &&
                record.data.all { it == ZERO }
        }

    private fun servfail(query: DnsQuestion) =
        DnsCodec.buildResponse(query, DnsRcode.SERVFAIL, emptyList())

    private companion object {
        const val BLOCKED_TTL = 60L
        const val IPV4_SIZE = 4
        const val IPV6_SIZE = 16
        const val ZERO: Byte = 0
    }
}
