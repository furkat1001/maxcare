package uz.maxcare.backend

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class AppConfigTest {
    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })

    @Test
    fun `server does not start without a secret key`() {
        assertFailsWith<IllegalStateException> { AppConfig.fromEnv(emptyMap()) }
    }

    @Test
    fun `secret key must be 32 bytes`() {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16))

        assertFailsWith<IllegalArgumentException> {
            AppConfig.fromEnv(mapOf("MAXCARE_SECRET_KEY" to shortKey))
        }
    }

    @Test
    fun `defaults point to local docker compose`() {
        val config = AppConfig.fromEnv(mapOf("MAXCARE_SECRET_KEY" to key))

        assertEquals(8080, config.port)
        assertEquals("jdbc:postgresql://localhost:5432/maxcare", config.dbUrl)
    }

    @Test
    fun `secrets are not printed`() {
        val config = AppConfig.fromEnv(
            mapOf("MAXCARE_SECRET_KEY" to key, "MAXCARE_DB_PASSWORD" to "db-secret-value")
        )

        assertFalse(config.toString().contains("db-secret-value"))
        assertFalse(config.toString().contains(key))
    }
}
