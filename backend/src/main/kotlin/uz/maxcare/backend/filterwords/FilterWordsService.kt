package uz.maxcare.backend.filterwords

import java.util.UUID
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.queryList
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.db.update
import uz.maxcare.backend.db.uuid

/**
 * Настройка фильтра семьи: свои стоп-слова (`block`), разрешённые слова (`allow`, отключают
 * корень из словаря) и уровень строгости. Телефон ребёнка забирает всё это одним запросом.
 */
class FilterWordsService(private val db: Db) {

    suspend fun list(familyId: UUID, kind: WordKind): List<String> = db.tx { c ->
        c.queryList(
            "select word from filter_words where family_id = ? and kind = ? order by word",
            familyId,
            kind.wire
        ) { it.getString("word") }
    }

    suspend fun add(familyId: UUID, raw: String, kind: WordKind): AddResult = db.tx { c ->
        val word = normalize(raw) ?: return@tx AddResult.Invalid
        val count = c.queryOne(
            "select count(*) as n from filter_words where family_id = ? and kind = ?",
            familyId,
            kind.wire
        ) { it.getInt("n") } ?: 0
        if (count >= MAX_WORDS) return@tx AddResult.LimitReached
        c.update(
            "insert into filter_words (family_id, word, kind) values (?, ?, ?) on conflict do nothing",
            familyId,
            word,
            kind.wire
        )
        AddResult.Added(word)
    }

    suspend fun remove(familyId: UUID, raw: String, kind: WordKind): Boolean = db.tx { c ->
        val word = normalize(raw) ?: return@tx false
        c.update(
            "delete from filter_words where family_id = ? and word = ? and kind = ?",
            familyId,
            word,
            kind.wire
        ) > 0
    }

    suspend fun strictness(familyId: UUID): Strictness = db.tx { c ->
        readStrictness(c, familyId)
    }

    suspend fun setStrictness(familyId: UUID, value: Strictness) = db.tx { c ->
        c.update(
            """
            insert into family_settings (family_id, strictness) values (?, ?)
            on conflict (family_id) do update set strictness = excluded.strictness, updated_at = now()
            """.trimIndent(),
            familyId,
            value.wire
        )
    }

    /** Весь конфиг для устройства ребёнка; null — устройства нет. */
    suspend fun deviceConfig(deviceId: UUID): DeviceFilterConfig? = db.tx { c ->
        val familyId = c.queryOne(
            "select family_id from devices where id = ?",
            deviceId
        ) { it.uuid("family_id") } ?: return@tx null
        DeviceFilterConfig(
            block = words(c, familyId, WordKind.BLOCK),
            allow = words(c, familyId, WordKind.ALLOW),
            strictness = readStrictness(c, familyId).wire
        )
    }

    private fun words(c: java.sql.Connection, familyId: UUID, kind: WordKind): List<String> =
        c.queryList(
            "select word from filter_words where family_id = ? and kind = ? order by word",
            familyId,
            kind.wire
        ) { it.getString("word") }

    private fun readStrictness(c: java.sql.Connection, familyId: UUID): Strictness {
        val value = c.queryOne(
            "select strictness from family_settings where family_id = ?",
            familyId
        ) { it.getString("strictness") }
        return Strictness.parse(value) ?: Strictness.STANDARD
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

enum class WordKind(val wire: String) {
    BLOCK("block"),
    ALLOW("allow");

    companion object {
        fun parse(value: String?): WordKind? = entries.firstOrNull { it.wire == value }
    }
}

enum class Strictness(val wire: String) {
    STANDARD("standard"),
    STRICT("strict");

    companion object {
        fun parse(value: String?): Strictness? = entries.firstOrNull { it.wire == value }
    }
}

data class DeviceFilterConfig(
    val block: List<String>,
    val allow: List<String>,
    val strictness: String
)

sealed interface AddResult {
    data class Added(val word: String) : AddResult
    data object Invalid : AddResult
    data object LimitReached : AddResult
}
