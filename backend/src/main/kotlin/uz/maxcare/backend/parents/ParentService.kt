package uz.maxcare.backend.parents

import java.sql.SQLException
import java.time.Clock
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.db.update
import uz.maxcare.backend.db.uuid
import uz.maxcare.backend.security.Argon2Hasher
import uz.maxcare.backend.security.Tokens

/** Родитель, вошедший по токену сессии. */
data class ParentPrincipal(val parentId: UUID, val familyId: UUID)

sealed interface RegisterResult {
    data class Created(val parentId: UUID) : RegisterResult
    data object EmailTaken : RegisterResult
    data object Invalid : RegisterResult
}

class ParentService(
    private val db: Db,
    private val hasher: Argon2Hasher,
    private val clock: Clock
) {
    // Для несуществующего e-mail всё равно считаем Argon2: по времени ответа нельзя узнать,
    // зарегистрирован ли адрес.
    private val dummyHash by lazy { hasher.hash("timing-equalizer-password") }

    suspend fun register(email: String, password: String): RegisterResult {
        val normalized = normalize(email)
        if (!isValidEmail(normalized) ||
            password.length !in PASSWORD_LENGTH
        ) {
            return RegisterResult.Invalid
        }

        val hash = withContext(Dispatchers.Default) { hasher.hash(password) }
        val parentId = UUID.randomUUID()
        return try {
            db.tx { c ->
                val familyId = UUID.randomUUID()
                c.update("insert into families (id) values (?)", familyId)
                c.update(
                    "insert into parents (id, family_id, email, password_hash) values (?, ?, ?, ?)",
                    parentId,
                    familyId,
                    normalized,
                    hash
                )
            }
            RegisterResult.Created(parentId)
        } catch (e: SQLException) {
            // Уникальность e-mail проверяет база: так нет гонки между проверкой и вставкой.
            if (e.sqlState == UNIQUE_VIOLATION) RegisterResult.EmailTaken else throw e
        }
    }

    /** @return токен сессии или null, если e-mail или пароль неверны (без уточнения, что именно). */
    suspend fun login(email: String, password: String): String? {
        val parent = db.tx { c ->
            c.queryOne("select id, password_hash from parents where email = ?", normalize(email)) {
                it.uuid("id") to it.getString("password_hash")
            }
        }
        val matches = withContext(Dispatchers.Default) {
            hasher.verify(password, parent?.second ?: dummyHash)
        }
        if (parent == null || !matches) return null

        val token = Tokens.generate()
        db.tx { c ->
            c.update(
                "insert into parent_sessions (token_hash, parent_id, expires_at) values (?, ?, ?)",
                Tokens.hash(token),
                parent.first,
                clock.instant().plus(SESSION_TTL)
            )
        }
        return token
    }

    suspend fun authenticate(token: String): ParentPrincipal? = db.tx { c ->
        c.queryOne(
            """
            select p.id, p.family_id from parent_sessions s
            join parents p on p.id = s.parent_id
            where s.token_hash = ? and s.expires_at > ?
            """.trimIndent(),
            Tokens.hash(token),
            clock.instant()
        ) { ParentPrincipal(parentId = it.uuid("id"), familyId = it.uuid("family_id")) }
    }

    private fun normalize(email: String) = email.trim().lowercase()

    private fun isValidEmail(email: String) =
        email.length <= MAX_EMAIL_LENGTH && EMAIL_PATTERN.matches(email)

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        const val MAX_EMAIL_LENGTH = 254
        val PASSWORD_LENGTH = 8..128
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        val SESSION_TTL = 30.days.toJavaDuration()
    }
}
