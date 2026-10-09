package uz.maxcare.backend.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * Пароли родителей: Argon2id, запись в формате PHC
 * `$argon2id$v=19$m=<КБ>,t=<итерации>,p=<потоки>$<соль>$<хеш>`.
 * Параметры по умолчанию — рекомендация OWASP (19 МБ памяти, 2 прохода).
 */
class Argon2Hasher(
    private val memoryKb: Int = 19_456,
    private val iterations: Int = 2,
    private val parallelism: Int = 1,
    private val random: SecureRandom = SecureRandom()
) {
    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val hash = derive(password, salt, memoryKb, iterations, parallelism, HASH_BYTES)
        return "\$argon2id\$v=19\$m=$memoryKb,t=$iterations,p=$parallelism" +
            "\$${encode(salt)}\$${encode(hash)}"
    }

    /** @throws IllegalArgumentException если запись в неизвестном формате. */
    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        require(parts.size == PHC_PARTS && parts[1] == "argon2id" && parts[2] == "v=19") {
            "Unknown password hash format"
        }
        val params = parts[3].split(',').associate { param ->
            val (name, value) = param.split('=', limit = 2)
            name to value.toInt()
        }
        val expected = decode(parts[5])
        val actual = derive(
            password,
            decode(parts[4]),
            params.getValue("m"),
            params.getValue("t"),
            params.getValue("p"),
            expected.size
        )
        return MessageDigest.isEqual(actual, expected)
    }

    @Suppress("LongParameterList")
    private fun derive(
        password: String,
        salt: ByteArray,
        memoryKb: Int,
        iterations: Int,
        parallelism: Int,
        length: Int
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .build()
        val output = ByteArray(length)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(password.toCharArray(), output)
        return output
    }

    private fun encode(bytes: ByteArray) =
        Base64.getEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(text: String) = Base64.getDecoder().decode(text)

    private companion object {
        const val SALT_BYTES = 16
        const val HASH_BYTES = 32
        const val PHC_PARTS = 6
    }
}
