package uz.maxcare.backend

import java.util.Base64

/**
 * Настройки из переменных окружения. Значения по умолчанию подходят только для локального
 * docker-compose; ключ шифрования по умолчанию не задан, без него сервер не стартует.
 */
class AppConfig(
    val port: Int,
    val dbUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val redisUrl: String,
    val secretKey: ByteArray,
    /** ID OAuth-клиентов Google (через запятую в MAXCARE_GOOGLE_CLIENT_IDS); пусто — вход через Google выключен. */
    val googleClientIds: Set<String> = emptySet(),
    /**
     * Сервер стоит за своим прокси (Caddy) и недоступен напрямую: IP клиента берётся из
     * `X-Forwarded-For`. Без прокси заголовку верить нельзя — его подделает кто угодно.
     */
    val behindProxy: Boolean = false
) {
    // Пароль базы и ключ не попадают в логи и сообщения об ошибках.
    override fun toString() =
        "AppConfig(port=$port, dbUrl=$dbUrl, dbUser=$dbUser, redisUrl=$redisUrl)"

    companion object {
        const val SECRET_KEY_BYTES = 32

        fun fromEnv(env: Map<String, String>): AppConfig {
            val key = env["MAXCARE_SECRET_KEY"]
                ?: error("MAXCARE_SECRET_KEY is not set: base64 of $SECRET_KEY_BYTES random bytes")
            val secretKey = Base64.getDecoder().decode(key)
            require(secretKey.size == SECRET_KEY_BYTES) {
                "MAXCARE_SECRET_KEY must be $SECRET_KEY_BYTES bytes, got ${secretKey.size}"
            }
            return AppConfig(
                port = env["PORT"]?.toInt() ?: DEFAULT_PORT,
                dbUrl = env["MAXCARE_DB_URL"] ?: "jdbc:postgresql://localhost:5432/maxcare",
                dbUser = env["MAXCARE_DB_USER"] ?: "maxcare",
                dbPassword = env["MAXCARE_DB_PASSWORD"] ?: "maxcare-dev",
                redisUrl = env["MAXCARE_REDIS_URL"] ?: "redis://localhost:6379",
                secretKey = secretKey,
                googleClientIds = env["MAXCARE_GOOGLE_CLIENT_IDS"].orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
                behindProxy = env["MAXCARE_BEHIND_PROXY"] == "true"
            )
        }

        private const val DEFAULT_PORT = 8080
    }
}
