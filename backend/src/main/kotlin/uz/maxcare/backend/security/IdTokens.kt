package uz.maxcare.backend.security

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.net.URI
import java.text.ParseException

/** Внешний вход родителя. [wire] — имя в адресе запроса и в базе (`parent_identities.provider`). */
enum class IdentityProvider(val wire: String) {
    GOOGLE("google"),
    APPLE("apple");

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Пользователь, которого подтвердил провайдер. [subject] — постоянный ID у провайдера;
 * [email] — в нижнем регистре; [emailVerified] — провайдер сам подтвердил владение адресом.
 */
data class VerifiedIdentity(
    val provider: IdentityProvider,
    val subject: String,
    val email: String?,
    val emailVerified: Boolean
)

fun interface IdTokenVerifier {
    /**
     * Блокирующий: при первом вызове и смене ключей скачивает ключи провайдера.
     * @return null — токен поддельный, просрочен, выдан другому приложению или не разобран.
     */
    fun verify(idToken: String): VerifiedIdentity?
}

/**
 * Проверка ID-токена (OpenID Connect) по открытым ключам провайдера: подпись RS256, срок,
 * издатель и получатель — наши ID клиентов. Без [audiences] (вход не настроен) ничего не принимает.
 */
class JwtIdTokenVerifier(
    private val provider: IdentityProvider,
    keys: JWKSource<SecurityContext>,
    private val issuers: Set<String>,
    private val audiences: Set<String>
) : IdTokenVerifier {
    private val processor = DefaultJWTProcessor<SecurityContext>().apply {
        jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, keys)
        jwtClaimsSetVerifier = DefaultJWTClaimsVerifier(
            audiences,
            null,
            setOf("sub", "iss", "exp", "iat"),
            null
        )
    }

    override fun verify(idToken: String): VerifiedIdentity? {
        if (audiences.isEmpty()) return null
        val claims = try {
            processor.process(idToken, null)
        } catch (ignored: ParseException) {
            null
        } catch (ignored: BadJOSEException) {
            null
        } catch (ignored: JOSEException) {
            null
        }
        return claims?.takeIf { it.issuer in issuers }?.let(::toIdentity)
    }

    private fun toIdentity(claims: JWTClaimsSet): VerifiedIdentity? = try {
        VerifiedIdentity(
            provider = provider,
            subject = claims.subject,
            email = claims.getStringClaim("email")?.trim()?.lowercase(),
            // Google присылает boolean, Apple — иногда строку "true".
            emailVerified = when (val value = claims.getClaim("email_verified")) {
                is Boolean -> value
                is String -> value == "true"
                else -> false
            }
        )
    } catch (ignored: ParseException) {
        // email не строкой — токен не нашего формата.
        null
    }

    companion object {
        private const val GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs"

        /** @param clientIds ID OAuth-клиентов MaxCare, для которых Google выпускает токены. */
        fun google(clientIds: Set<String>) = JwtIdTokenVerifier(
            IdentityProvider.GOOGLE,
            JWKSourceBuilder.create<SecurityContext>(URI(GOOGLE_KEYS).toURL()).build(),
            setOf("https://accounts.google.com", "accounts.google.com"),
            clientIds
        )
    }
}
