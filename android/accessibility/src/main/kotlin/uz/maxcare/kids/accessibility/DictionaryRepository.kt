package uz.maxcare.kids.accessibility

import javax.inject.Inject
import javax.inject.Singleton
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.readString
import uz.maxcare.kids.core.storage.writeString

/** Базовый словарь из assets; внедряется отдельно, чтобы репозиторий тестировался без Android. */
class BaseDictionary(val stems: List<String>)

/**
 * Актуальный словарь фильтра: базовый (asset) + свои стоп-слова родителя (с сервера).
 *
 * Делает словарь обновляемым без релиза. [current] отдаёт готовый матчер на горячем пути
 * (служба зовёт его на каждом событии текста), а [loadCached]/[refresh] перестраивают его
 * в фоне. Родительские слова кэшируются в [SecureStore] — offline-first.
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

    /** Дешёвый вызов для горячего пути: возвращает текущий матчер. */
    fun current(): ForbiddenWords = matcher

    /** Подмешать кэшированные родительские слова (без сети). */
    suspend fun loadCached() {
        rebuild(readParentWords())
    }

    /** Забрать слова с сервера, сохранить и перестроить; при ошибке сети оставить кэш. */
    suspend fun refresh() {
        val token = store.readString(SecureKeys.DEVICE_TOKEN) ?: return
        val result = api.fetchFilterWords(token)
        if (result is ApiResult.Ok) {
            storeParentWords(result.value)
            rebuild(result.value)
        }
    }

    private fun rebuild(parentWords: List<String>) {
        matcher = ForbiddenWords(baseStems + parentWords)
    }

    private suspend fun readParentWords(): List<String> =
        store.readString(SecureKeys.PARENT_FILTER_WORDS)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private suspend fun storeParentWords(words: List<String>) {
        store.writeString(SecureKeys.PARENT_FILTER_WORDS, words.joinToString("\n"))
    }
}
