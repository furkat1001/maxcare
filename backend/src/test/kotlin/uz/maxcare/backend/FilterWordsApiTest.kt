package uz.maxcare.backend

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import uz.maxcare.backend.filterwords.FilterConfigResponse
import uz.maxcare.backend.filterwords.FilterWordRequest
import uz.maxcare.backend.filterwords.FilterWordsResponse
import uz.maxcare.backend.filterwords.SettingsRequest
import uz.maxcare.backend.filterwords.SettingsResponse

class FilterWordsApiTest : ApiTestBase() {

    private suspend fun HttpClient.addWord(word: String, token: String, kind: String? = null) =
        postJson(path("/v1/filter-words", kind), FilterWordRequest(word), token)

    private suspend fun HttpClient.words(token: String, kind: String? = null) =
        getJson(path("/v1/filter-words", kind), token).body<FilterWordsResponse>().words

    private suspend fun HttpClient.config(deviceToken: String) =
        getJson("/v1/device/filter-config", deviceToken).body<FilterConfigResponse>()

    private suspend fun HttpClient.deleteWord(word: String, token: String, kind: String? = null) =
        delete("/v1/filter-words") {
            bearerAuth(token)
            parameter("word", word)
            if (kind != null) parameter("kind", kind)
        }

    private fun path(base: String, kind: String?) = if (kind == null) base else "$base?kind=$kind"

    @Test
    fun `parent adds a block word and sees it`() = apiTest { client ->
        val parent = client.registerAndLogin()
        assertEquals(HttpStatusCode.Created, client.addWord("казино", parent).status)
        assertEquals(listOf("казино"), client.words(parent))
    }

    @Test
    fun `block and allow words are separate lists`() = apiTest { client ->
        val parent = client.registerAndLogin()
        client.addWord("казино", parent, kind = "block")
        client.addWord("оружие", parent, kind = "allow")

        assertEquals(listOf("казино"), client.words(parent, kind = "block"))
        assertEquals(listOf("оружие"), client.words(parent, kind = "allow"))
    }

    @Test
    fun `device config returns block, allow and strictness`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val device = client.pairedDevice(parent)
        client.addWord("ставки", parent, kind = "block")
        client.addWord("оружие", parent, kind = "allow")

        val config = client.config(device.deviceToken)

        assertEquals(listOf("ставки"), config.block)
        assertEquals(listOf("оружие"), config.allow)
        assertEquals("standard", config.strictness)
    }

    @Test
    fun `strictness defaults to standard and can be set to strict`() = apiTest { client ->
        val parent = client.registerAndLogin()
        assertEquals("standard", client.getJson("/v1/settings", parent).body<SettingsResponse>().strictness)

        assertEquals("strict", client.setStrict(parent))
    }

    @Test
    fun `invalid strictness is rejected`() = apiTest { client ->
        val parent = client.registerAndLogin()
        val response = client.put("/v1/settings") {
            bearerAuth(parent)
            contentType(ContentType.Application.Json)
            setBody(SettingsRequest("extreme"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `words are isolated per family`() = apiTest { client ->
        val parentA = client.registerAndLogin()
        val parentB = client.registerAndLogin()
        client.addWord("казино", parentA)
        assertEquals(emptyList(), client.words(parentB))
    }

    @Test
    fun `blank word is rejected`() = apiTest { client ->
        val parent = client.registerAndLogin()
        assertEquals(HttpStatusCode.BadRequest, client.addWord("   ", parent).status)
    }

    @Test
    fun `delete removes a word of the given kind`() = apiTest { client ->
        val parent = client.registerAndLogin()
        client.addWord("оружие", parent, kind = "allow")
        assertEquals(HttpStatusCode.NoContent, client.deleteWord("оружие", parent, kind = "allow").status)
        assertEquals(emptyList(), client.words(parent, kind = "allow"))
    }

    // PUT /v1/settings strict, возвращает новое значение.
    private suspend fun HttpClient.setStrict(token: String): String {
        val response = put("/v1/settings") {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(SettingsRequest("strict"))
        }
        return response.body<SettingsResponse>().strictness
    }
}
