package uz.maxcare.kids.setup.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Языки интерфейса MaxCare Kids — те же, что в ресурсах (`values`, `uz`, `uz-Cyrl`, `ru`).
 * Подписи — на самом языке: их узнают, даже если сейчас интерфейс на незнакомом языке.
 * Без флагов: флаг — это страна, а латиница и кириллица в узбекском под одним флагом.
 * @param label сокращение на кнопке меню; @param fullName строка в самом меню.
 */
enum class KidsLanguage(val tag: String, val label: String, val fullName: String) {
    UZBEK_LATIN("uz", "O‘zb", "O‘zbekcha (lotin)"),
    UZBEK_CYRILLIC("uz-Cyrl", "Ўзб", "Ўзбекча (кирилл)"),
    RUSSIAN("ru", "Рус", "Русский"),
    ENGLISH("en", "Eng", "English");

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
