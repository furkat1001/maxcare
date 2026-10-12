package uz.maxcare.kids.accessibility

import android.content.Context

/**
 * Источник словаря стоп-слов. Сейчас — встроенный asset: словарь лежит данными, а не в коде,
 * и его можно заменить без правки Kotlin. Шов для будущего: шифрование (Tink) и обновление с
 * сервера без релиза — тем же механизмом, что и ИИ-модели (см. docs/sprint-3-4-plan.md, PR 4).
 */
object DictionarySource {

    private const val ASSET = "forbidden_words.txt"

    /** Сырые строки словаря; комментарии (`#`) и пустые строки отброшены. Нормализация — в [ForbiddenWords]. */
    fun loadStems(context: Context): List<String> =
        context.assets.open(ASSET).bufferedReader().useLines { lines ->
            lines.map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }
}
