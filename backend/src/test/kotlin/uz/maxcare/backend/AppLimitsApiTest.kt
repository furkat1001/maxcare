package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import uz.maxcare.backend.limits.AppLimitsDto
import uz.maxcare.backend.limits.LimitRuleDto

class AppLimitsApiTest : ApiTestBase() {

    // Синтетические правила — не настройки настоящей семьи.
    private val video =
        LimitRuleDto("group", "video", dailyLimitMin = 60, sessionMin = 15, breakMin = 15)
    private val game = LimitRuleDto("app", "com.example.blockgame", dailyLimitMin = 90)
    private val limits =
        AppLimitsDto(listOf(video, game), alwaysAllowed = listOf("com.example.school"))

    private suspend fun HttpClient.putLimits(deviceId: String, token: String, body: AppLimitsDto) =
        put("/v1/devices/$deviceId/app-limits") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    @Test
    fun `a new device has no limits`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        val response = client.getJson("/v1/devices/${device.deviceId}/app-limits", token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(AppLimitsDto(), response.body<AppLimitsDto>())
    }

    @Test
    fun `parent sets limits and the device reads them`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        val saved = client.putLimits(device.deviceId, token, limits)
        assertEquals(HttpStatusCode.OK, saved.status)

        val forDevice = client.getJson("/v1/device/app-limits", device.deviceToken)
        assertEquals(HttpStatusCode.OK, forDevice.status)
        val body = forDevice.body<AppLimitsDto>()
        assertEquals(listOf(game, video), body.rules)
        assertEquals(listOf("com.example.school"), body.alwaysAllowed)
    }

    @Test
    fun `a new set replaces the old one`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)

        client.putLimits(device.deviceId, token, limits)
        client.putLimits(device.deviceId, token, AppLimitsDto(listOf(video)))

        val body = client.getJson("/v1/devices/${device.deviceId}/app-limits", token)
            .body<AppLimitsDto>()
        assertEquals(AppLimitsDto(listOf(video)), body)
    }

    @Test
    fun `another family's device is not found`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        val stranger = client.registerAndLogin()

        assertEquals(
            HttpStatusCode.NotFound,
            client.getJson("/v1/devices/${device.deviceId}/app-limits", stranger).status
        )
        assertEquals(
            HttpStatusCode.NotFound,
            client.putLimits(device.deviceId, stranger, limits).status
        )
    }

    @Test
    fun `limits need sessions`() = apiTest { client ->
        val device = client.pairedDevice(client.registerAndLogin())
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.getJson("/v1/devices/${device.deviceId}/app-limits").status
        )
        assertEquals(HttpStatusCode.Unauthorized, client.getJson("/v1/device/app-limits").status)
    }

    @Test
    fun `invalid rules are rejected`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)
        val invalid = listOf(
            LimitRuleDto("group", "news", dailyLimitMin = 30), // неизвестная группа
            LimitRuleDto("app", "not a package", dailyLimitMin = 30),
            LimitRuleDto("site", "example.com", dailyLimitMin = 30),
            LimitRuleDto("group", "video"), // нет ни лимита, ни цикла
            LimitRuleDto("group", "video", sessionMin = 15), // сессия без перерыва
            LimitRuleDto("group", "video", dailyLimitMin = 0),
            LimitRuleDto("group", "video", dailyLimitMin = 24 * 60 + 1),
            LimitRuleDto("group", "video", sessionMin = 15, breakMin = 241)
        )

        invalid.forEach { rule ->
            val response = client.putLimits(device.deviceId, token, AppLimitsDto(listOf(rule)))
            assertEquals(HttpStatusCode.BadRequest, response.status, rule.toString())
        }
    }

    @Test
    fun `duplicate targets are rejected`() = apiTest { client ->
        val token = client.registerAndLogin()
        val device = client.pairedDevice(token)
        val twice = AppLimitsDto(listOf(video, video.copy(dailyLimitMin = 30)))
        val allowedTwice = AppLimitsDto(alwaysAllowed = listOf("com.example.a", "com.example.a"))

        assertEquals(
            HttpStatusCode.BadRequest,
            client.putLimits(device.deviceId, token, twice).status
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.putLimits(device.deviceId, token, allowedTwice).status
        )
    }
}
