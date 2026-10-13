package uz.maxcare.backend.filterwords

import java.util.UUID
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.queryList
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.db.update
import uz.maxcare.backend.db.uuid

/** Свои стоп-слова семьи. Телефон ребёнка забирает их и добавляет к базовому словарю фильтра. */
class FilterWordsService(private val db: Db) {

    suspend fun list(familyId: UUID): List<String> = db.tx { c ->
        c.queryList(
            "select word from filter_words where family_id = ? order by word",
            familyId
        ) { it.getString("word") }
    }

    /** Список для устройства ребёнка; null — устройства нет. */
    suspend fun forDevice(deviceId: UUID): List<String>? = db.tx { c ->
        val familyId = c.queryOne(
            "select family_id from devices where id = ?",
            deviceId
        ) { it.uuid("family_id") } ?: return@tx null
        c.queryList(
            "select word from filter_words where family_id = ? order by word",
            familyId
        ) { it.getString("word") }
    }

    suspend fun add(familyId: UUID, raw: String): AddResult = db.tx { c ->
        val word = normalize(raw) ?: return@tx AddResult.Invalid
        val count = c.queryOne(
            "select count(*) as n from filter_words where family_id = ?",
            familyId
        ) { it.getInt("n") } ?: 0
        if (count >= MAX_WORDS) return@tx AddResult.LimitReached
        c.update(
            "insert into filter_words (family_id, word) values (?, ?) on conflict do nothing",
            familyId,
            word
        )
        AddResult.Added(word)
    }

    suspend fun remove(familyId: UUID, raw: String): Boolean = db.tx { c ->
        val word = normalize(raw) ?: return@tx false
        c.update("delete from filter_words where family_id = ? and word = ?", familyId, word) > 0
    }

    /** Храним только trim + нижний регистр; полная нормализация (транслит, leet) — на телефоне. */
    private fun normalize(raw: String): String? {
        val word = raw.trim().lowercase()
        return word.takeIf { it.isNotEmpty() && it.length <= MAX_WORD_LENGTH }
    }

    private companion object {
        const val MAX_WORDS = 200
        const val MAX_WORD_LENGTH = 64
    }
}

sealed interface AddResult {
    data class Added(val word: String) : AddResult
    data object Invalid : AddResult
    data object LimitReached : AddResult
}
