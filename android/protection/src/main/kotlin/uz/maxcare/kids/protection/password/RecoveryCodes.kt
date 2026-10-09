package uz.maxcare.kids.protection.password

import java.nio.ByteBuffer
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Коды восстановления — TOTP (RFC 6238) на HMAC-SHA256, 8 цифр, интервал 5 минут.
 * Должен совпадать с сервером: `backend/.../security/RecoveryCodes.kt`, `docs/backend-api.md`.
 */
internal object RecoveryCodes {
    const val STEP_SECONDS = 300L
    const val DIGITS = 8

    private const val MODULO = 100_000_000
    private const val OFFSET_MASK = 0x0f
    private const val SIGN_MASK = 0x7f
    private const val BYTE_MASK = 0xff

    fun counterAt(instant: Instant): Long = instant.epochSecond / STEP_SECONDS

    @Suppress("MagicNumber") // Динамическое усечение из RFC 4226, раздел 5.3
    fun code(secret: ByteArray, counter: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        val hash = mac.doFinal(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(counter).array())
        val offset = hash.last().toInt() and OFFSET_MASK
        val binary = ((hash[offset].toInt() and SIGN_MASK) shl 24) or
            ((hash[offset + 1].toInt() and BYTE_MASK) shl 16) or
            ((hash[offset + 2].toInt() and BYTE_MASK) shl 8) or
            (hash[offset + 3].toInt() and BYTE_MASK)
        return (binary % MODULO).toString().padStart(DIGITS, '0')
    }
}
