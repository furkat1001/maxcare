package uz.maxcare.backend

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.lettuce.core.RedisClient
import java.time.Clock
import kotlinx.serialization.json.Json
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.devices.DeviceService
import uz.maxcare.backend.devices.PARENT_AUTH
import uz.maxcare.backend.devices.deviceRoutes
import uz.maxcare.backend.http.installErrorHandling
import uz.maxcare.backend.parents.ParentService
import uz.maxcare.backend.parents.parentRoutes
import uz.maxcare.backend.ratelimit.RedisRateLimiter
import uz.maxcare.backend.security.Argon2Hasher
import uz.maxcare.backend.security.SecretBox

fun main() {
    val config = AppConfig.fromEnv(System.getenv())
    val dataSource = Db.connect(config.dbUrl, config.dbUser, config.dbPassword)
    val redisClient = RedisClient.create(config.redisUrl)
    val redis = redisClient.connect()
    val db = Db(dataSource)
    val clock = Clock.systemUTC()
    val services = Services(
        parents = ParentService(db, Argon2Hasher(), clock),
        devices = DeviceService(db, SecretBox(config.secretKey), clock),
        rateLimiter = RedisRateLimiter(redis)
    )

    embeddedServer(Netty, port = config.port) {
        maxcareModule(services)
        monitor.subscribe(ApplicationStopped) {
            redis.close()
            redisClient.shutdown()
            dataSource.close()
        }
    }.start(wait = true)
}

fun Application.maxcareModule(services: Services) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    installErrorHandling()
    install(Authentication) {
        bearer(PARENT_AUTH) {
            authenticate { credential -> services.parents.authenticate(credential.token) }
        }
    }
    routing {
        get("/health") { call.respondText("ok") }
        parentRoutes(services.parents, services.rateLimiter)
        deviceRoutes(services.devices, services.rateLimiter)
    }
}
