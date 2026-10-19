package uz.maxcare.backend.limits

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.Serializable
import uz.maxcare.backend.devices.DevicePrincipal
import uz.maxcare.backend.devices.PARENT_AUTH
import uz.maxcare.backend.events.DEVICE_AUTH
import uz.maxcare.backend.http.ErrorCodes
import uz.maxcare.backend.http.respondError
import uz.maxcare.backend.parents.ParentPrincipal
import uz.maxcare.backend.ratelimit.RateLimiter
import uz.maxcare.backend.ratelimit.check

/**
 * @param kind `app` — [target] это пакет; `group` — [target] это группа: video, social, game, audio.
 */
@Serializable
data class LimitRuleDto(
    val kind: String,
    val target: String,
    val dailyLimitMin: Int? = null,
    val sessionMin: Int? = null,
    val breakMin: Int? = null
)

@Serializable
data class AppLimitsDto(
    val rules: List<LimitRuleDto> = emptyList(),
    val alwaysAllowed: List<String> = emptyList()
)

fun Route.appLimitsRoutes(service: AppLimitsService, rateLimiter: RateLimiter) {
    authenticate(PARENT_AUTH) {
        get("/v1/devices/{deviceId}/app-limits") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            val limits = call.deviceIdParam()?.let { service.forParent(parent.familyId, it) }
            if (limits == null) {
                // Чужое и несуществующее устройство неотличимы: не раскрываем, какие ID есть.
                call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)
            } else {
                call.respond(limits.toDto())
            }
        }

        put("/v1/devices/{deviceId}/app-limits") {
            val parent = requireNotNull(call.principal<ParentPrincipal>())
            rateLimiter.check("app-limits:${parent.familyId}", limit = 120, window = 1.hours)
            val deviceId = call.deviceIdParam()
            val limits = call.receive<AppLimitsDto>().parse()
            when {
                limits == null ->
                    call.respondError(HttpStatusCode.BadRequest, ErrorCodes.INVALID_REQUEST)

                deviceId == null || !service.replace(parent.familyId, deviceId, limits) ->
                    call.respondError(HttpStatusCode.NotFound, ErrorCodes.NOT_FOUND)

                else -> call.respond(limits.toDto())
            }
        }
    }

    authenticate(DEVICE_AUTH) {
        get("/v1/device/app-limits") {
            val device = requireNotNull(call.principal<DevicePrincipal>())
            call.respond(service.forDevice(device.deviceId).toDto())
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.deviceIdParam(): UUID? =
    parameters["deviceId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

/** Весь набор отклоняется, если хоть одно правило испорчено: это ошибка клиента. */
private fun AppLimitsDto.parse(): AppLimits? {
    val parsed = rules.takeIf { it.size <= MAX_RULES }?.map { it.parse() }
    val allowedOk = alwaysAllowed.size <= MAX_ALLOWED &&
        alwaysAllowed.all { it.isPackageName() } &&
        alwaysAllowed.toSet().size == alwaysAllowed.size
    if (parsed == null || null in parsed || !allowedOk) return null
    val list = parsed.filterNotNull()
    val unique = list.map { it.kind to it.target }.toSet().size == list.size
    return if (unique) AppLimits(list, alwaysAllowed) else null
}

private fun LimitRuleDto.parse(): LimitRule? {
    val kind = LimitKind.parse(kind) ?: return null
    val targetOk = when (kind) {
        LimitKind.APP -> target.isPackageName()
        LimitKind.GROUP -> target in GROUPS
    }
    val rule = LimitRule(kind, target, dailyLimitMin, sessionMin, breakMin)
    return rule.takeIf { targetOk && it.minutesAreValid() }
}

private fun LimitRule.minutesAreValid(): Boolean {
    val dailyOk = dailyLimitMin == null || dailyLimitMin in 1..MINUTES_PER_DAY
    // Сессия и перерыв — только вместе: перерыв без сессии ничего не значит.
    val cycleOk = (sessionMin == null && breakMin == null) ||
        (sessionMin in 1..MAX_CYCLE_MIN && breakMin in 1..MAX_CYCLE_MIN)
    val hasLimit = dailyLimitMin != null || sessionMin != null
    return dailyOk && cycleOk && hasLimit
}

private fun String.isPackageName() = length in 1..MAX_PACKAGE && PACKAGE.matches(this)

private fun AppLimits.toDto() = AppLimitsDto(
    rules = rules.map {
        LimitRuleDto(it.kind.wire, it.target, it.dailyLimitMin, it.sessionMin, it.breakMin)
    },
    alwaysAllowed = alwaysAllowed
)

/** Группы — категории приложений Android (`ApplicationInfo.category`), см. телефон. */
private val GROUPS = setOf("video", "social", "game", "audio")

private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")

private const val MAX_RULES = 50
private const val MAX_ALLOWED = 100
private const val MAX_PACKAGE = 255
private const val MINUTES_PER_DAY = 24 * 60
private const val MAX_CYCLE_MIN = 240
