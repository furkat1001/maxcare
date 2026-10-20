package uz.maxcare.kids.accessibility

/**
 * Текст ввода, который проверяет фильтр, или null — проверять нечего.
 *
 * Поля пароля не проверяются никогда, в том числе когда символы показаны «глазком»: пароль —
 * не поисковый запрос, и читать его незачем даже в памяти. Иначе пароль родителя со словом
 * из словаря стирался бы на экране MaxCare Kids, а родителю уходило бы ложное событие.
 *
 * Поле (как поиск Google) отдаёт текст через узел; где узел пуст (как omnibox Chrome) —
 * берём текст самого события.
 */
internal fun inputText(
    eventIsPassword: Boolean,
    nodeIsPassword: Boolean,
    nodeText: CharSequence?,
    eventText: List<CharSequence>
): String? {
    if (eventIsPassword || nodeIsPassword) return null
    return nodeText?.toString()?.ifBlank { null }
        ?: eventText.joinToString(" ").ifBlank { null }
}
