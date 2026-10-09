package uz.maxcare.backend.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование секретов в базе (AES-256-GCM). Формат: 12 байт nonce + шифртекст с тегом.
 * [associatedData] привязывает шифртекст к записи: секрет одного устройства нельзя
 * подставить в строку другого.
 */
class SecretBox(key: ByteArray) {
    private val keySpec = SecretKeySpec(key.copyOf(), "AES")
    private val random = SecureRandom()

    init {
        require(key.size == KEY_BYTES) { "Key must be $KEY_BYTES bytes" }
    }

    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(associatedData)
        return nonce + cipher.doFinal(plaintext)
    }

    /** @throws javax.crypto.AEADBadTagException если данные изменены или ключ не тот. */
    fun decrypt(box: ByteArray, associatedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            keySpec,
            GCMParameterSpec(TAG_BITS, box, 0, NONCE_BYTES)
        )
        cipher.updateAAD(associatedData)
        return cipher.doFinal(box, NONCE_BYTES, box.size - NONCE_BYTES)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
    }
}
