package uz.maxcare.kids.setup.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class KidsLanguageTest {
    @Test
    fun `uzbek script decides between latin and cyrillic`() {
        assertEquals(KidsLanguage.UZBEK_LATIN, KidsLanguage.of(Locale.forLanguageTag("uz")))
        assertEquals(KidsLanguage.UZBEK_LATIN, KidsLanguage.of(Locale.forLanguageTag("uz-Latn-UZ")))
        assertEquals(KidsLanguage.UZBEK_CYRILLIC, KidsLanguage.of(Locale.forLanguageTag("uz-Cyrl")))
        assertEquals(
            KidsLanguage.UZBEK_CYRILLIC,
            KidsLanguage.of(Locale.forLanguageTag("uz-Cyrl-UZ"))
        )
    }

    @Test
    fun `russian and english are recognised`() {
        assertEquals(KidsLanguage.RUSSIAN, KidsLanguage.of(Locale.forLanguageTag("ru-UZ")))
        assertEquals(KidsLanguage.ENGLISH, KidsLanguage.of(Locale.forLanguageTag("en-US")))
    }

    @Test
    fun `a phone language without a translation shows english`() {
        assertEquals(KidsLanguage.ENGLISH, KidsLanguage.of(Locale.forLanguageTag("kk-KZ")))
    }

    @Test
    fun `tags match the resource folders`() {
        assertEquals(listOf("uz", "uz-Cyrl", "ru", "en"), KidsLanguage.entries.map { it.tag })
    }
}
