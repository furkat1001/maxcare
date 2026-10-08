package uz.maxcare.kids.protection.password

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    private val hasher = PasswordHasher(iterations = 1_000)

    @Test
    fun `correct password matches, wrong does not`() {
        val record = hasher.hash("secret1".toCharArray())

        assertTrue(hasher.verify("secret1".toCharArray(), record))
        assertFalse(hasher.verify("secret2".toCharArray(), record))
    }

    @Test
    fun `same password gets different records thanks to salt`() {
        assertNotEquals(
            hasher.hash("secret1".toCharArray()),
            hasher.hash("secret1".toCharArray())
        )
    }

    @Test
    fun `record does not contain the password`() {
        val record = hasher.hash("secret1".toCharArray())

        assertFalse(record.contains("secret1"))
    }

    @Test
    fun `old record still verifies after iteration count changes`() {
        val oldRecord = PasswordHasher(iterations = 500).hash("secret1".toCharArray())

        assertTrue(hasher.verify("secret1".toCharArray(), oldRecord))
    }

    @Test
    fun `non-latin password works`() {
        val record = hasher.hash("Ҳимоя-o‘g‘il".toCharArray())

        assertTrue(hasher.verify("Ҳимоя-o‘g‘il".toCharArray(), record))
    }

    @Test
    fun `damaged record is reported`() {
        listOf(
            "",
            "v1:abc:xx:yy",
            "v2:1000:AAAA:AAAA",
            "v1:0:AAAA:AAAA",
            "v1:1000:%%%:AAAA"
        ).forEach { record ->
            assertThrows(IllegalArgumentException::class.java) {
                hasher.verify("secret1".toCharArray(), record)
            }
        }
    }
}
