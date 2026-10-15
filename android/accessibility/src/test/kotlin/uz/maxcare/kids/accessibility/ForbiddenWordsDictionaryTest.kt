package uz.maxcare.kids.accessibility

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Проверки на настоящем встроенном словаре, а не на тестовом наборе. */
class ForbiddenWordsDictionaryTest {

    private val words = ForbiddenWords(
        File("src/main/assets/forbidden_words.txt").useLines(block = DictionarySource::parse)
    )

    @Test
    fun `betting phrases are caught in both scripts`() {
        assertTrue(words.matches("ставки на спорт"))
        assertTrue(words.matches("лучшая СТАВКА НА СПОРТ сегодня"))
        assertTrue(words.matches("с т а в к и на спорт"))
        assertTrue(words.matches("stavki na sport"))
        assertTrue(words.matches("sport stavka"))
    }

    @Test
    fun `delivery and similar everyday words are not blocked`() {
        assertFalse(words.matches("доставка еды"))
        assertFalse(words.matches("условия доставки"))
        assertFalse(words.matches("заставка мультфильма"))
        assertFalse(words.matches("поставки товаров"))
        assertFalse(words.matches("dostavka bepul"))
    }
}
