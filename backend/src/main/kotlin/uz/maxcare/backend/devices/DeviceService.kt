package uz.maxcare.backend.devices

import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.instantOrNull
import uz.maxcare.backend.db.queryList
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.db.update
import uz.maxcare.backend.db.uuid
import uz.maxcare.backend.security.RecoveryCodes
import uz.maxcare.backend.security.SecretBox
import uz.maxcare.backend.security.Tokens

data class PairingCode(val code: String, val expiresAt: Instant)

sealed interface PairResult {
    /** [recoverySecret] уходит на телефон один раз; на сервере он хранится зашифрованным. */
    class Paired(val deviceId: UUID, val deviceToken: String, val recoverySecret: ByteArray) :
        PairResult

    data object InvalidCode : PairResult
}

data class RecoveryCode(val code: String, val validUntil: Instant)

/** Телефон ребёнка, обратившийся с токеном устройства. */
data class DevicePrincipal(val deviceId: UUID)

/** Устройство ребёнка для списка в приложении родителя. */
data class DeviceSummary(
    val id: UUID,
    val name: String,
    val childName: String?,
    val platform: String,
    val lastSeenAt: Instant?
)

enum class Platform(val value: String) {
    ANDROID("android"),
    IOS("ios");

    companion object {
        fun parse(value: String) = entries.firstOrNull { it.value == value }
    }
}

/** Привязка телефона ребёнка к семье и коды восстановления. */
class DeviceService(
    private val db: Db,
    private val secretBox: SecretBox,
    private val clock: Clock,
    private val random: SecureRandom = SecureRandom()
) {
    suspend fun createPairingCode(familyId: UUID, childName: String?): PairingCode {
        repeat(MAX_CODE_ATTEMPTS) {
            val code = random.nextInt(CODE_MODULO).toString().padStart(CODE_DIGITS, '0')
            val now = clock.instant()
            val expiresAt = now.plus(PAIRING_CODE_TTL)
            val inserted = db.tx { c ->
                // Старые коды больше не нужны и только повышают шанс совпадения нового.
                c.update(
                    "delete from pairing_codes where expires_at < ?",
                    now.minus(EXPIRED_CODE_RETENTION)
                )
                c.update(
                    """
                    insert into pairing_codes (code, family_id, child_name, expires_at)
                    values (?, ?, ?, ?) on conflict (code) do nothing
                    """.trimIndent(),
                    code,
                    familyId,
                    childName,
                    expiresAt
                )
            }
            if (inserted == 1) return PairingCode(code, expiresAt)
        }
        error("Could not allocate a unique pairing code")
    }

    suspend fun pair(code: String, deviceName: String, platform: Platform): PairResult =
        db.tx { c ->
            val now = clock.instant()
            // Код гасится в той же транзакции, что и создаётся устройство: дважды его не использовать.
            val claimed = c.queryOne(
                """
            update pairing_codes set used_at = ?
            where code = ? and used_at is null and expires_at > ?
            returning family_id, child_name
                """.trimIndent(),
                now,
                code,
                now
            ) { it.uuid("family_id") to it.getString("child_name") }
                ?: return@tx PairResult.InvalidCode

            val deviceId = UUID.randomUUID()
            val token = Tokens.generate()
            val secret = ByteArray(RECOVERY_SECRET_BYTES).also(random::nextBytes)
            c.update(
                """
            insert into devices (id, family_id, child_name, name, platform, token_hash, recovery_secret_enc)
            values (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                deviceId,
                claimed.first,
                claimed.second,
                deviceName,
                platform.value,
                Tokens.hash(token),
                secretBox.encrypt(secret, associatedData(deviceId))
            )
            PairResult.Paired(deviceId, token, secret)
        }

    /** Устройства семьи, новые сверху. */
    suspend fun listDevices(familyId: UUID): List<DeviceSummary> = db.tx { c ->
        c.queryList(
            """
            select id, name, child_name, platform, last_seen_at from devices
            where family_id = ? order by paired_at desc
            """.trimIndent(),
            familyId
        ) { row ->
            DeviceSummary(
                id = row.uuid("id"),
                name = row.getString("name"),
                childName = row.getString("child_name"),
                platform = row.getString("platform"),
                lastSeenAt = row.instantOrNull("last_seen_at")
            )
        }
    }

    /** Телефон по токену устройства; null — токен неизвестен (например, после повторной привязки). */
    suspend fun authenticate(token: String): DevicePrincipal? = db.tx { c ->
        c.queryOne("select id from devices where token_hash = ?", Tokens.hash(token)) {
            DevicePrincipal(it.uuid("id"))
        }
    }

    /** @return null, если устройства нет или оно принадлежит другой семье. */
    suspend fun recoveryCode(familyId: UUID, deviceId: UUID): RecoveryCode? {
        val encrypted = db.tx { c ->
            c.queryOne(
                "select recovery_secret_enc from devices where id = ? and family_id = ?",
                deviceId,
                familyId
            ) { it.getBytes("recovery_secret_enc") }
        } ?: return null

        val secret = secretBox.decrypt(encrypted, associatedData(deviceId))
        val counter = RecoveryCodes.counterAt(clock.instant())
        // Телефон принимает код текущего и предыдущего интервала.
        val validUntil = Instant.ofEpochSecond((counter + 2) * RecoveryCodes.STEP_SECONDS)
        return RecoveryCode(RecoveryCodes.code(secret, counter), validUntil)
    }

    private fun associatedData(deviceId: UUID) = deviceId.toString().encodeToByteArray()

    private companion object {
        const val CODE_DIGITS = 8
        const val CODE_MODULO = 100_000_000
        const val MAX_CODE_ATTEMPTS = 5
        const val RECOVERY_SECRET_BYTES = 32
        val PAIRING_CODE_TTL = 10.minutes.toJavaDuration()
        val EXPIRED_CODE_RETENTION = 1.days.toJavaDuration()
    }
}
