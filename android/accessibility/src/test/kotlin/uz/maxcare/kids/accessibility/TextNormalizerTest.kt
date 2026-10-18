package uz.maxcare.kids.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {

    @Test
    fun `lowercases and drops separators and punctuation`() {
        assertEquals(normalize("П О Р Н!"), normalize("порн"))
    }

    @Test
    fun `collapses repeated letters`() {
        assertEquals(normalize("пооорннн"), normalize("порн"))
    }

    @Test
    fun `undoes leet substitutions`() {
        assertEquals(normalize("porn"), normalize("p0rn"))
        assertEquals(normalize("sex"), normalize("s3x"))
    }

    @Test
    fun `folds latin and cyrillic homoglyphs to one form`() {
        // «porn» латиницей и «рorn» со смешением сводятся к одному канону.
        assertEquals(normalize("porn"), normalize("рorn"))
        // Латинская «o» внутри кириллического слова больше не обходит фильтр.
        assertEquals(normalize("порно"), normalize("пoрнo"))
    }

    @Test
    fun `strips uzbek apostrophes`() {
        assertEquals(normalize("og"), normalize("o‘g‘"))
    }

    @Test
    fun `blank input becomes empty`() {
        assertEquals("", normalize("   "))
        assertEquals("", normalize(null))
    }

    private fun normalize(input: String?) = TextNormalizer.normalize(input)
}
