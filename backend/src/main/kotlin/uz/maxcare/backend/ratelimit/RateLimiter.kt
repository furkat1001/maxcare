package uz.maxcare.backend.ratelimit

import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import kotlin.time.Duration
import kotlinx.coroutines.future.await

/** Ограничение частоты запросов: не больше [limit] за [window] на один ключ. */
interface RateLimiter {
    /** @return true, если запрос укладывается в лимит. */
    suspend fun tryAcquire(key: String, limit: Int, window: Duration): Boolean
}

class RateLimitedException : RuntimeException("Too many requests")

/** @throws RateLimitedException если лимит исчерпан. */
suspend fun RateLimiter.check(key: String, limit: Int, window: Duration) {
    if (!tryAcquire(key, limit, window)) throw RateLimitedException()
}

/**
 * Фиксированное окно в Redis. Счётчик и срок жизни ставятся одним Lua-скриптом атомарно:
 * если процесс упадёт между INCR и PEXPIRE, ключ без срока жизни заблокировал бы клиента навсегда.
 */
class RedisRateLimiter(private val connection: StatefulRedisConnection<String, String>) :
    RateLimiter {
    override suspend fun tryAcquire(key: String, limit: Int, window: Duration): Boolean {
        val count = connection.async()
            .eval<Long>(
                SCRIPT,
                ScriptOutputType.INTEGER,
                arrayOf("ratelimit:$key"),
                window.inWholeMilliseconds.toString()
            )
            .await()
        return count <= limit
    }

    private companion object {
        val SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
        """.trimIndent()
    }
}
