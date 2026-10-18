package uz.maxcare.kids.accessibility

/**
 * Матчер стоп-слов. Хранит корни слов в нормализованном виде и ищет их как подстроку —
 * так ловятся словоформы («порн» → «порно», «порнуха»).
 *
 * [allowStems] — разрешённые слова: их нормализованные корни исключаются из [stems], поэтому
 * родитель может отключить корень, который даёт ложные срабатывания. Предсказуемо и без обхода
 * «допиши разрешённое слово»: отключается именно корень.
 *
 * Чистый класс без Android: словарь приходит в конструктор (из [DictionarySource] в проде,
 * из явного набора в тестах). И слова словаря, и проверяемый текст проходят [TextNormalizer],
 * поэтому обход регистром/цифрами/смешением алфавитов не помогает.
 *
 * Корни короче [MIN_STEM] после нормализации отбрасываются: повторы букв схлопываются, и «xxx»
 * становился бы одной «х», а матч подстрокой — срабатыванием на любой текст.
 */
class ForbiddenWords(rawStems: Collection<String>, allowStems: Collection<String> = emptyList()) {

    private val stems: Set<String> = run {
        val allowed = allowStems.map(TextNormalizer::normalize).filter { it.isNotEmpty() }.toSet()
        rawStems.map(TextNormalizer::normalize)
            .filter { it.length >= MIN_STEM && it !in allowed }
            .toSet()
    }

    fun matches(rawText: String): Boolean {
        val normalized = TextNormalizer.normalize(rawText)
        return normalized.isNotEmpty() && stems.any { normalized.contains(it) }
    }

    private companion object {
        const val MIN_STEM = 3
    }
}
