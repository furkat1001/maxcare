package uz.maxcare.kids.accessibility

import java.util.Locale

/**
 * ВРЕМЕННЫЙ матчер стоп-слов — только чтобы проверить сквозной путь службы на телефоне.
 *
 * В PR 2 заменяется нормализацией (регистр, латиница/кириллица, транслит, замены букв
 * цифрами/символами, повторы) и словарём из шифрованных assets / remote config, обновляемым
 * без релиза. Здесь — маленький очевидный набор корней и простейшее приведение регистра.
 */
internal object StopWords {

    private val stems = setOf("порн", "porn", "секс", "sex", "xxx", "наркот", "narkot")

    fun matches(text: String): Boolean {
        val normalized = text.lowercase(Locale.ROOT)
        return stems.any { normalized.contains(it) }
    }
}
