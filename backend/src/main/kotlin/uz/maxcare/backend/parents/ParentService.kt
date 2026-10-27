package uz.maxcare.backend.parents

import java.sql.Connection
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uz.maxcare.backend.db.Db
import uz.maxcare.backend.db.instantOrNull
import uz.maxcare.backend.db.queryList
import uz.maxcare.backend.db.queryOne
import uz.maxcare.backend.db.update
import uz.maxcare.backend.db.uuid
import uz.maxcare.backend.security.Argon2Hasher
import uz.maxcare.backend.security.Tokens
import uz.maxcare.backend.security.VerifiedIdentity

/** Длина пробного периода новой семьи; совпадает с бэкфиллом в миграции V10. */
const val TRIAL_DAYS = 14

/** Аккаунт родителя. [providers] — связанные входы: `google`, `apple`. */
data class Account(
    val email: String,
    val hasPassword: Boolean,
    val createdAt: Instant,
    val providers: List<String>,
    val trialEndsAt: Instant
)

/** Родитель, вошедший по токену сессии. */
data class ParentPrincipal(val parentId: UUID, val familyId: UUID)

sealed interface RegisterResult {
    data class Created(val parentId: UUID) : RegisterResult
    data object EmailTaken : RegisterResult
    data object Invalid : RegisterResult
}

sealed interface IdentityLoginResult {
    data class Session(val token: String) : IdentityLoginResult

    /** Адрес уже занят аккаунтом с паролем, а провайдер не подтвердил, что адрес принадлежит этому человеку. */
    data object EmailTaken : IdentityLoginResult

    /** Провайдер не сообщил e-mail: без него новый аккаунт не создать. */
    data object NoEmail : IdentityLoginResult
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
                val familyId = createFamily(c)
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
        // У родителя, вошедшего только через Google, пароля нет: такой вход всегда отклоняется,
        // но Argon2 считается всё равно, чтобы время ответа не выдавало такие аккаунты.
        val hash = parent?.second
        val matches = withContext(Dispatchers.Default) {
            hasher.verify(password, hash ?: dummyHash)
        }
        if (parent == null || hash == null || !matches) return null
        return createSession(parent.first)
    }

    /**
     * Вход через Google или Apple. Порядок: уже связанный аккаунт; иначе аккаунт с тем же
     * e-mail, если провайдер подтвердил адрес (связываем); иначе новый аккаунт без пароля.
     */
    suspend fun loginWithIdentity(identity: VerifiedIdentity): IdentityLoginResult {
        val outcome = try {
            db.tx { c -> resolveIdentity(c, identity) }
        } catch (e: SQLException) {
            // Два первых входа одновременно: второй упрётся в уникальность — тогда аккаунт уже есть.
            if (e.sqlState != UNIQUE_VIOLATION) throw e
            db.tx { c -> resolveIdentity(c, identity) }
        }
        return when (outcome) {
            is IdentityOutcome.Parent -> IdentityLoginResult.Session(createSession(outcome.id))
            IdentityOutcome.EmailTaken -> IdentityLoginResult.EmailTaken
            IdentityOutcome.NoEmail -> IdentityLoginResult.NoEmail
        }
    }

    private fun resolveIdentity(c: Connection, identity: VerifiedIdentity): IdentityOutcome {
        val linked = c.queryOne(
            "select parent_id from parent_identities where provider = ? and subject = ?",
            identity.provider.wire,
            identity.subject
        ) { it.uuid("parent_id") }
        val email = identity.email?.let(::normalize)
        return when {
            linked != null -> IdentityOutcome.Parent(linked)
            email == null -> IdentityOutcome.NoEmail
            else -> linkOrCreate(c, identity, email)
        }
    }

    private fun linkOrCreate(
        c: Connection,
        identity: VerifiedIdentity,
        email: String
    ): IdentityOutcome {
        val existing = c.queryOne("select id from parents where email = ?", email) { it.uuid("id") }
        if (existing != null && !identity.emailVerified) return IdentityOutcome.EmailTaken
        val parentId = existing ?: UUID.randomUUID().also { id ->
            val familyId = createFamily(c)
            c.update(
                "insert into parents (id, family_id, email) values (?, ?, ?)",
                id,
                familyId,
                email
            )
        }
        c.update(
            "insert into parent_identities (provider, subject, parent_id) values (?, ?, ?)",
            identity.provider.wire,
            identity.subject,
            parentId
        )
        return IdentityOutcome.Parent(parentId)
    }

    // Пробный период отсчитывается по часам сервера (в тестах — по тестовым), а не по now() базы.
    private fun createFamily(c: Connection): UUID {
        val familyId = UUID.randomUUID()
        c.update(
            "insert into families (id, trial_ends_at) values (?, ?)",
            familyId,
            clock.instant().plus(TRIAL)
        )
        return familyId
    }

    /** Аккаунт для экрана настроек: адрес, способы входа и пробный период семьи. */
    suspend fun account(principal: ParentPrincipal): Account = db.tx { c ->
        val account = requireNotNull(
            c.queryOne(
                """
                select p.email, p.password_hash is not null as has_password, p.created_at,
                       f.trial_ends_at
                from parents p join families f on f.id = p.family_id
                where p.id = ?
                """.trimIndent(),
                principal.parentId
            ) {
                Account(
                    email = it.getString("email"),
                    hasPassword = it.getBoolean("has_password"),
                    createdAt = requireNotNull(it.instantOrNull("created_at")),
                    providers = emptyList(),
                    trialEndsAt = requireNotNull(it.instantOrNull("trial_ends_at"))
                )
            }
        ) { "Session belongs to a parent that no longer exists" }
        val providers = c.queryList(
            "select provider from parent_identities where parent_id = ? order by provider",
            principal.parentId
        ) { it.getString("provider") }
        account.copy(providers = providers)
    }

    private suspend fun createSession(parentId: UUID): String {
        val token = Tokens.generate()
        db.tx { c ->
            c.update(
                "insert into parent_sessions (token_hash, parent_id, expires_at) values (?, ?, ?)",
                Tokens.hash(token),
                parentId,
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
        val TRIAL = TRIAL_DAYS.days.toJavaDuration()
    }
}

private sealed interface IdentityOutcome {
    data class Parent(val id: UUID) : IdentityOutcome
    data object EmailTaken : IdentityOutcome
    data object NoEmail : IdentityOutcome
}
