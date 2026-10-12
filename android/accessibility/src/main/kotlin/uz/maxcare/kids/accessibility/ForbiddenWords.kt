package uz.maxcare.kids.accessibility

/**
 * Матчер стоп-слов. Хранит корни слов в нормализованном виде и ищет их как подстроку —
 * так ловятся словоформы («порн» → «порно», «порнуха»).
 *
 * Чистый класс без Android: словарь приходит в конструктор (из [DictionarySource] в проде,
 * из явного набора в тестах). И слова словаря, и проверяемый текст проходят [TextNormalizer],
 * поэтому обход регистром/цифрами/смешением алфавитов не помогает.
 */
class ForbiddenWords(rawStems: Collection<String>) {

    private val stems: Set<String> =
        rawStems.map(TextNormalizer::normalize).filter { it.isNotEmpty() }.toSet()

    fun matches(rawText: String): Boolean {
        val normalized = TextNormalizer.normalize(rawText)
        return normalized.isNotEmpty() && stems.any { normalized.contains(it) }
    }
}
