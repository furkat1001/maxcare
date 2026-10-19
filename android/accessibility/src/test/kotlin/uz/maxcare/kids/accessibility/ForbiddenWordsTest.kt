package uz.maxcare.kids.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForbiddenWordsTest {

    private val words = ForbiddenWords(listOf("порн", "porn", "секс", "наркот"))

    @Test
    fun `stems that collapse to a letter or two are ignored`() {
        val short = ForbiddenWords(listOf("xxx", "хх", "ab"))
        assertFalse(short.matches("usage-test@example.test"))
        assertFalse(short.matches("Xiaomi"))
    }

    @Test
    fun `matches a stem inside a word form`() {
        assertTrue(words.matches("порнуха"))
        assertTrue(words.matches("наркотики купить"))
    }

    @Test
    fun `matches despite case, repeats and leet`() {
        assertTrue(words.matches("ПОООРН"))
        assertTrue(words.matches("p0rn"))
    }

    @Test
    fun `matches across mixed latin and cyrillic`() {
        assertTrue(words.matches("пoрно")) // латинская «o»
    }

    @Test
    fun `does not match safe text`() {
        assertFalse(words.matches("математика 5 класс"))
        assertFalse(words.matches(""))
    }

    @Test
    fun `empty dictionary matches nothing`() {
        assertFalse(ForbiddenWords(emptyList()).matches("порно"))
    }
}
