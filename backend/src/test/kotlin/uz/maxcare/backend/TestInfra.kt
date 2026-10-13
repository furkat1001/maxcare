package uz.maxcare.backend

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.sql.DataSource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.devices.DeviceService
import uz.maxcare.backend.events.EventService
import uz.maxcare.backend.filterwords.FilterWordsService
import uz.maxcare.backend.location.LocationService
import uz.maxcare.backend.parents.ParentService
import uz.maxcare.backend.ratelimit.RedisRateLimiter
import uz.maxcare.backend.security.Argon2Hasher
import uz.maxcare.backend.security.SecretBox

/**
 * Настоящие PostgreSQL и Redis в Docker — один раз на весь прогон тестов.
 * Данные только синтетические.
 */
object TestInfra {
    private val postgres = PostgreSQLContainer("postgres:17-alpine").apply { start() }

    private val redisContainer = RedisContainer().apply { start() }

    val dataSource: DataSource by lazy {
        Db.connect(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    val redis: StatefulRedisConnection<String, String> by lazy {
        RedisClient.create(
            "redis://${redisContainer.host}:${redisContainer.getMappedPort(REDIS_PORT)}"
        )
            .connect()
    }

    val secretKey = ByteArray(AppConfig.SECRET_KEY_BYTES) { it.toByte() }

    /** Быстрые параметры Argon2: в тестах проверяется логика, а не стойкость. */
    fun services(clock: Clock): Services {
        val db = Db(dataSource)
        return Services(
            parents = ParentService(db, Argon2Hasher(memoryKb = 64, iterations = 1), clock),
            devices = DeviceService(db, SecretBox(secretKey), clock),
            events = EventService(db, clock),
            locations = LocationService(db, clock),
            filterWords = FilterWordsService(db),
            rateLimiter = RedisRateLimiter(redis)
        )
    }

    fun flushRedis() {
        redis.sync().flushall()
    }

    private const val REDIS_PORT = 6379

    private class RedisContainer : GenericContainer<RedisContainer>("redis:8-alpine") {
        init {
            withExposedPorts(REDIS_PORT)
        }
    }
}

/** Часы, которые тест двигает вручную: истечение кодов проверяется без ожидания. */
class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
