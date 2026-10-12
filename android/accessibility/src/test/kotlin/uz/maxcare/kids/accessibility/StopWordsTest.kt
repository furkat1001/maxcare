package uz.maxcare.kids.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopWordsTest {

    @Test
    fun `matches a stop word regardless of case`() {
        assertTrue(StopWords.matches("Порно"))
        assertTrue(StopWords.matches("PORN"))
    }

    @Test
    fun `matches a stop word inside a longer query`() {
        assertTrue(StopWords.matches("смотреть секс онлайн"))
    }

    @Test
    fun `does not match safe text`() {
        assertFalse(StopWords.matches("математика 5 класс"))
        assertFalse(StopWords.matches(""))
    }
}
