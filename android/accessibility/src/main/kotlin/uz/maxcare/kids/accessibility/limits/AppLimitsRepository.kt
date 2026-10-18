package uz.maxcare.kids.accessibility.limits

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uz.maxcare.kids.core.limits.AppLimitRule
import uz.maxcare.kids.core.limits.AppLimitsConfig
import uz.maxcare.kids.core.limits.LimitKind
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/**
 * Ограничения родителя: с сервера, с кэшем в [SecureStore] — без сети работают последние
 * полученные правила (offline-first, как словарь фильтра).
 */
@Singleton
class AppLimitsRepository @Inject constructor(
    private val store: SecureStore,
    private val api: MaxCareApi
) {
    @Volatile
    private var config = AppLimitsConfig()

    fun current(): AppLimitsConfig = config

    suspend fun loadCached() {
        val cached = store.readString(SecureKeys.APP_LIMITS) ?: return
        decode(cached)?.let { config = it }
    }

    /** Забрать правила с сервера; при ошибке сети оставить кэш. */
    suspend fun refresh() {
        val token = store.readString(SecureKeys.DEVICE_TOKEN) ?: return
        val result = api.fetchAppLimits(token)
        if (result is ApiResult.Ok) {
            config = result.value
            store.writeString(SecureKeys.APP_LIMITS, encode(result.value))
        }
    }

    @Serializable
    private data class CachedRule(
        val kind: String,
        val target: String,
        val dailyLimitMin: Int? = null,
        val sessionMin: Int? = null,
        val breakMin: Int? = null
    )

    @Serializable
    private data class Cached(val rules: List<CachedRule>, val alwaysAllowed: List<String>)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun encode(config: AppLimitsConfig): String = json.encodeToString(
            Cached.serializer(),
            Cached(
                config.rules.map {
                    CachedRule(
                        it.kind.wire,
                        it.target,
                        it.dailyLimitMin,
                        it.sessionMin,
                        it.breakMin
                    )
                },
                config.alwaysAllowed.toList()
            )
        )

        fun decode(text: String): AppLimitsConfig? = try {
            val cached = json.decodeFromString(Cached.serializer(), text)
            AppLimitsConfig(
                cached.rules.mapNotNull { rule ->
                    LimitKind.parse(rule.kind)?.let {
                        AppLimitRule(
                            it,
                            rule.target,
                            rule.dailyLimitMin,
                            rule.sessionMin,
                            rule.breakMin
                        )
                    }
                },
                cached.alwaysAllowed.toSet()
            )
        } catch (ignored: SerializationException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }
    }
}
