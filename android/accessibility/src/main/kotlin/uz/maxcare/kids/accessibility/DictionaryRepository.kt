package uz.maxcare.kids.accessibility

import javax.inject.Inject
import javax.inject.Singleton
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.FilterConfig
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/** Базовый словарь из assets; внедряется отдельно, чтобы репозиторий тестировался без Android. */
class BaseDictionary(val stems: List<String>)

/**
 * Актуальная настройка фильтра: базовый словарь (asset) + свои стоп-слова и разрешённые слова
 * родителя (с сервера) + уровень строгости. Обновляется без релиза.
 *
 * [current] отдаёт готовый матчер на горячем пути; [imageThreshold] — порог анализа картинок
 * по строгости. [loadCached]/[refresh] перестраивают всё в фоне; настройки кэшируются
 * в [SecureStore] (offline-first).
 */
@Singleton
class DictionaryRepository @Inject constructor(
    base: BaseDictionary,
    private val store: SecureStore,
    private val api: MaxCareApi
) {
    private val baseStems: List<String> = base.stems

    @Volatile
    private var matcher: ForbiddenWords = ForbiddenWords(baseStems)

    @Volatile
    private var strict: Boolean = false

    /** Дешёвый вызов для горячего пути: текущий матчер текста. */
    fun current(): ForbiddenWords = matcher

    /** Порог анализа картинок: Strict ловит больше ценой ложных. */
    fun imageThreshold(): Float = if (strict) STRICT_THRESHOLD else STANDARD_THRESHOLD

    /** Подмешать кэшированную настройку (без сети). */
    suspend fun loadCached() {
        apply(readWords(SecureKeys.PARENT_FILTER_WORDS), readWords(SecureKeys.PARENT_ALLOW_WORDS))
        strict = store.readString(SecureKeys.FILTER_STRICTNESS) == STRICT
    }

    /** Забрать настройку с сервера, сохранить и перестроить; при ошибке сети оставить кэш. */
    suspend fun refresh() {
        val token = store.readString(SecureKeys.DEVICE_TOKEN) ?: return
        val result = api.fetchFilterConfig(token)
        if (result is ApiResult.Ok) {
            store(result.value)
            apply(result.value.block, result.value.allow)
            strict = result.value.strictness == STRICT
        }
    }

    private fun apply(block: List<String>, allow: List<String>) {
        matcher = ForbiddenWords(baseStems + block, allow)
    }

    private suspend fun store(config: FilterConfig) {
        store.writeString(SecureKeys.PARENT_FILTER_WORDS, config.block.joinToString("\n"))
        store.writeString(SecureKeys.PARENT_ALLOW_WORDS, config.allow.joinToString("\n"))
        store.writeString(SecureKeys.FILTER_STRICTNESS, config.strictness)
    }

    private suspend fun readWords(key: String): List<String> =
        store.readString(key)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val STRICT = "strict"

        // Пороги анализа картинок; standard совпадает с базовым, strict — ниже (ловит больше).
        const val STANDARD_THRESHOLD = 0.5f
        const val STRICT_THRESHOLD = 0.35f
    }
}
