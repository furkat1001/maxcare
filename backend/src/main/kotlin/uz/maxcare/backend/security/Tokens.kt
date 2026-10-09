package uz.maxcare.backend.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Токены сессий родителей и устройств: 256 случайных бит. В базе хранится только SHA-256 —
 * медленный хеш не нужен, перебирать 256 бит бессмысленно.
 */
object Tokens {
    private const val TOKEN_BYTES = 32
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray())
}
