package uz.maxcare.kids.protection.password

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

/**
 * PBKDF2-HMAC-SHA256 со случайной солью. Встроен в Android, отдельная библиотека не нужна.
 *
 * Формат записи: `v1:<итерации>:<соль base64>:<хеш base64>`. Число итераций хранится в записи,
 * поэтому его можно увеличить, не ломая уже сохранённые пароли.
 */
internal class PasswordHasher(
    private val iterations: Int,
    private val random: SecureRandom = SecureRandom()
) {
    @Inject
    constructor() : this(DEFAULT_ITERATIONS)

    fun hash(password: CharArray): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = pbkdf2(password, salt, iterations)
        return listOf(VERSION, iterations.toString(), encode(salt), encode(hash)).joinToString(":")
    }

    /** @throws IllegalArgumentException если запись повреждена или в неизвестном формате. */
    fun verify(password: CharArray, record: String): Boolean {
        val parts = record.split(':')
        require(parts.size == RECORD_PARTS && parts[0] == VERSION) {
            "Unknown password record format"
        }
        val recordIterations = parts[1].toInt()
        require(recordIterations in 1..MAX_ITERATIONS) { "Bad iteration count" }
        val expected = decode(parts[3])
        // Сравнение за постоянное время: по скорости ответа нельзя угадать совпавшие байты.
        return MessageDigest.isEqual(pbkdf2(password, decode(parts[2]), recordIterations), expected)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun decode(text: String) = Base64.getDecoder().decode(text)

    companion object {
        /**
         * Ориентир — около 0,5 с на телефоне среднего класса. Значение предварительное:
         * уточнить замером на бюджетном телефоне (Tecno/Infinix).
         */
        const val DEFAULT_ITERATIONS = 310_000

        private const val VERSION = "v1"
        private const val RECORD_PARTS = 4
        private const val MAX_ITERATIONS = 10_000_000
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256
    }
}
