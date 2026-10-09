package uz.maxcare.backend.security

import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class Argon2HasherTest {
    private val hasher = Argon2Hasher(memoryKb = 64, iterations = 1)

    @Test
    fun `correct password matches, wrong does not`() {
        val hash = hasher.hash("synthetic-password-1")

        assertTrue(hasher.verify("synthetic-password-1", hash))
        assertFalse(hasher.verify("synthetic-password-2", hash))
    }

    @Test
    fun `hash is argon2id with salt`() {
        val first = hasher.hash("synthetic-password-1")

        assertTrue(first.startsWith("\$argon2id\$v=19\$m=64,t=1,p=1\$"))
        assertNotEquals(first, hasher.hash("synthetic-password-1"))
    }

    @Test
    fun `hash made with other parameters still verifies`() {
        val strong = Argon2Hasher(memoryKb = 128, iterations = 2).hash("synthetic-password-1")

        assertTrue(hasher.verify("synthetic-password-1", strong))
    }

    @Test
    fun `unknown format is reported`() {
        assertFailsWith<IllegalArgumentException> { hasher.verify("x", "\$bcrypt\$whatever") }
    }
}

class SecretBoxTest {
    private val box = SecretBox(ByteArray(32) { it.toByte() })
    private val plaintext = "recovery-secret".encodeToByteArray()
    private val deviceA = "device-a".encodeToByteArray()

    @Test
    fun `round trip`() {
        assertContentEquals(plaintext, box.decrypt(box.encrypt(plaintext, deviceA), deviceA))
    }

    @Test
    fun `secret of one device cannot be used for another`() {
        val sealed = box.encrypt(plaintext, deviceA)

        assertFailsWith<AEADBadTagException> { box.decrypt(sealed, "device-b".encodeToByteArray()) }
    }

    @Test
    fun `tampering is detected`() {
        val sealed = box.encrypt(plaintext, deviceA)
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 1).toByte()

        assertFailsWith<AEADBadTagException> { box.decrypt(sealed, deviceA) }
    }

    @Test
    fun `wrong key cannot decrypt`() {
        val other = SecretBox(ByteArray(32) { 7 })

        assertFailsWith<AEADBadTagException> {
            other.decrypt(box.encrypt(plaintext, deviceA), deviceA)
        }
    }

    @Test
    fun `key must be 32 bytes`() {
        assertFailsWith<IllegalArgumentException> { SecretBox(ByteArray(16)) }
    }
}
