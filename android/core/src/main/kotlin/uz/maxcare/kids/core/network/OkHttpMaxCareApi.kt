package uz.maxcare.kids.core.network

import java.io.IOException
import java.util.Base64
import javax.net.ssl.SSLException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Привязка сертификата задаётся не здесь, а в Network Security Config приложения:
 * OkHttp на Android проверяет сертификаты через системный TrustManager, который её применяет.
 *
 * Тела запросов и ответов не логируются: в них коды, токены и секреты.
 */
internal class OkHttpMaxCareApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val ioDispatcher: CoroutineDispatcher
) : MaxCareApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pairDevice(code: String, deviceName: String): ApiResult<PairedDevice> =
        post("v1/devices/pair", PairRequest(code, deviceName, PLATFORM)) { response: PairResponse ->
            PairedDevice(
                deviceId = response.deviceId,
                deviceToken = response.deviceToken,
                recoverySecret = Base64.getDecoder().decode(response.recoverySecret)
            )
        }

    override suspend fun uploadEvents(
        deviceToken: String,
        events: List<OutgoingEvent>
    ): ApiResult<Unit> = post(
        "v1/device/events",
        EventBatchRequest(events.map { EventDto(it.id, it.type, it.occurredAt, it.detail) }),
        bearerToken = deviceToken
    ) { _: EventBatchResponse -> }

    override suspend fun uploadLocations(
        deviceToken: String,
        locations: List<OutgoingLocation>
    ): ApiResult<Unit> = post(
        "v1/device/locations",
        LocationBatchRequest(
            locations.map {
                LocationDto(it.id, it.latitude, it.longitude, it.accuracyMeters, it.recordedAt)
            }
        ),
        bearerToken = deviceToken
    ) { _: LocationBatchResponse -> }

    override suspend fun fetchFilterConfig(deviceToken: String): ApiResult<FilterConfig> =
        get("v1/device/filter-config", deviceToken) { response: FilterConfigResponse ->
            FilterConfig(response.block, response.allow, response.strictness)
        }

    private suspend inline fun <reified Res, T> get(
        path: String,
        bearerToken: String,
        crossinline map: (Res) -> T
    ): ApiResult<T> = withContext(ioDispatcher) {
        val httpRequest = Request.Builder()
            .url(baseUrl.resolve(path) ?: error("Bad API path: $path"))
            .get()
            .header("Authorization", "Bearer $bearerToken")
            .build()
        execute(httpRequest, map)
    }

    private suspend inline fun <reified Req, reified Res, T> post(
        path: String,
        request: Req,
        bearerToken: String? = null,
        crossinline map: (Res) -> T
    ): ApiResult<T> = withContext(ioDispatcher) {
        val httpRequest = Request.Builder()
            .url(baseUrl.resolve(path) ?: error("Bad API path: $path"))
            .post(json.encodeToString(request).toRequestBody(JSON_TYPE))
            .apply { bearerToken?.let { header("Authorization", "Bearer $it") } }
            .build()
        execute(httpRequest, map)
    }

    private inline fun <reified Res, T> execute(
        httpRequest: Request,
        map: (Res) -> T
    ): ApiResult<T> = try {
        client.newCall(httpRequest).execute().use { response ->
            val body = response.body.string()
            if (response.isSuccessful) {
                ApiResult.Ok(map(json.decodeFromString<Res>(body)))
            } else {
                ApiResult.HttpError(response.code, errorCode(body))
            }
        }
    } catch (ignored: SSLException) {
        // Раньше IOException: SSLException — её подкласс, но означает другое.
        ApiResult.InsecureConnection
    } catch (ignored: IOException) {
        ApiResult.NoConnection
    } catch (ignored: IllegalArgumentException) {
        // SerializationException и ошибки Base64 — подклассы IllegalArgumentException.
        ApiResult.BadResponse
    }

    private fun errorCode(body: String): String? =
        runCatching { json.decodeFromString<ErrorResponse>(body).error }.getOrNull()

    @Serializable
    private data class PairRequest(val code: String, val deviceName: String, val platform: String)

    @Serializable
    private data class PairResponse(
        val deviceId: String,
        val deviceToken: String,
        val recoverySecret: String
    )

    @Serializable
    private data class ErrorResponse(val error: String)

    @Serializable
    private data class EventDto(
        val id: String,
        val type: String,
        val occurredAt: String,
        val detail: String? = null
    )

    @Serializable
    private data class EventBatchRequest(val events: List<EventDto>)

    @Serializable
    private data class EventBatchResponse(val accepted: Int)

    @Serializable
    private data class LocationDto(
        val id: String,
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Double? = null,
        val recordedAt: String
    )

    @Serializable
    private data class LocationBatchRequest(val locations: List<LocationDto>)

    @Serializable
    private data class LocationBatchResponse(val accepted: Int)

    @Serializable
    private data class FilterConfigResponse(
        val block: List<String>,
        val allow: List<String>,
        val strictness: String
    )

    private companion object {
        const val PLATFORM = "android"
        val JSON_TYPE = "application/json".toMediaType()
    }
}
