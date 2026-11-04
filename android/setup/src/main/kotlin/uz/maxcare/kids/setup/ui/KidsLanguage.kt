package uz.maxcare.kids.setup.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Языки интерфейса MaxCare Kids — те же, что в ресурсах (`values`, `uz`, `uz-Cyrl`, `ru`).
 * Подпись — на самом языке: её узнают, даже если сейчас интерфейс на незнакомом языке.
 */
enum class KidsLanguage(val tag: String, val label: String) {
    UZBEK_LATIN("uz", "O‘zb"),
    UZBEK_CYRILLIC("uz-Cyrl", "Ўзб"),
    RUSSIAN("ru", "Рус"),
    ENGLISH("en", "Eng");

    companion object {
        /** Язык, на котором сейчас показан интерфейс; незнакомый язык телефона — английский. */
        fun of(locale: Locale): KidsLanguage = when (locale.language) {
            "uz" -> if (locale.script == "Cyrl") UZBEK_CYRILLIC else UZBEK_LATIN
            "ru" -> RUSSIAN
            else -> ENGLISH
        }
    }
}

/**
 * Язык только для MaxCare Kids, независимо от языка телефона. С Android 13 — системная настройка
 * языка приложения (та же, что в Настройках Android); до него выбор хранит AppCompat.
 * Экран пересоздаётся на новом языке сам.
 */
internal fun applyLanguage(language: KidsLanguage) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
}
