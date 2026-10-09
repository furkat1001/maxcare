package uz.maxcare.kids.setup.pairing

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.maxcare.kids.core.network.ApiResult
import uz.maxcare.kids.core.network.MaxCareApi
import uz.maxcare.kids.core.network.OutgoingEvent
import uz.maxcare.kids.core.network.PairedDevice
import uz.maxcare.kids.core.pairing.PairingResult
import uz.maxcare.kids.core.storage.SecureKeys
import uz.maxcare.kids.core.storage.SecureStore
import uz.maxcare.kids.core.storage.SecureStoreException
import uz.maxcare.kids.core.storage.readString

class ApiDevicePairingTest {
    private val store = InMemorySecureStore()
    private val api = FakeApi()
    private val pairing = ApiDevicePairing(api, store) { "Test phone" }

    @Test
    fun `successful pairing stores id, token and secret`() = runTest {
        assertEquals(PairingResult.Paired, pairing.pair("12345678"))

        assertTrue(pairing.isPaired())
        assertEquals("device-1", store.readString(SecureKeys.DEVICE_ID))
        assertEquals("token-1", store.readString(SecureKeys.DEVICE_TOKEN))
        assertArrayEquals(byteArrayOf(1, 2, 3), store.read(SecureKeys.RECOVERY_SECRET))
        assertEquals("12345678" to "Test phone", api.lastRequest)
    }

    @Test
    fun `spaces and dashes are allowed in the code`() = runTest {
        pairing.pair(" 1234-5678 ")

        assertEquals("12345678", api.lastRequest?.first)
    }

    @Test
    fun `wrong format is rejected without a request`() = runTest {
        listOf("1234567", "123456789", "1234abcd", "١٢٣٤٥٦٧٨").forEach { code ->
            assertEquals(code, PairingResult.InvalidFormat, pairing.pair(code))
        }
        assertNull(api.lastRequest)
    }

    @Test
    fun `already paired phone is not paired again`() = runTest {
        pairing.pair("12345678")
        api.lastRequest = null

        assertEquals(PairingResult.AlreadyPaired, pairing.pair("87654321"))
        assertNull(api.lastRequest)
    }

    @Test
    fun `server answers are mapped`() = runTest {
        mapOf(
            ApiResult.HttpError(400, "invalid_code") to PairingResult.InvalidCode,
            ApiResult.HttpError(429, "rate_limited") to PairingResult.TooManyAttempts,
            ApiResult.HttpError(500, "internal_error") to PairingResult.ServerError,
            ApiResult.NoConnection to PairingResult.NoConnection,
            ApiResult.InsecureConnection to PairingResult.InsecureConnection,
            ApiResult.BadResponse to PairingResult.ServerError
        ).forEach { (apiResult, expected) ->
            api.result = apiResult
            assertEquals(expected, pairing.pair("12345678"))
            assertFalse(pairing.isPaired())
        }
    }

    @Test
    fun `unreadable token means not paired`() = runTest {
        pairing.pair("12345678")
        store.broken += SecureKeys.DEVICE_TOKEN

        assertFalse(pairing.isPaired())
    }

    private class FakeApi : MaxCareApi {
        var result: ApiResult<PairedDevice> =
            ApiResult.Ok(PairedDevice("device-1", "token-1", byteArrayOf(1, 2, 3)))
        var lastRequest: Pair<String, String>? = null

        override suspend fun pairDevice(code: String, deviceName: String): ApiResult<PairedDevice> {
            lastRequest = code to deviceName
            return result
        }

        override suspend fun uploadEvents(deviceToken: String, events: List<OutgoingEvent>) =
            error("Not used")
    }

    private class InMemorySecureStore : SecureStore {
        private val values = mutableMapOf<String, ByteArray>()
        val broken = mutableSetOf<String>()

        override suspend fun read(key: String): ByteArray? {
            if (key in broken) throw SecureStoreException("broken", IOException())
            return values[key]?.copyOf()
        }

        override suspend fun write(key: String, value: ByteArray) {
            values[key] = value.copyOf()
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }
}
