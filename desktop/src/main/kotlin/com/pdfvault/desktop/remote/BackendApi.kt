package com.pdfvault.desktop.remote

import com.pdfvault.desktop.data.AuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Thrown for any non-2xx backend response (or when the backend isn't configured). */
class BackendException(val status: Int, message: String) : Exception(message)

@Serializable
private data class ErrorDto(val error: String? = null)

/**
 * Backend HTTP client using the built-in JDK [HttpClient] (no extra deps) + kotlinx-serialization.
 * Base URL + bearer token come from [AuthStore]; when the URL is blank, [enabled] is false and
 * calls fail fast.
 */
object BackendApi {
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val enabled: Boolean get() = AuthStore.enabled

    // --- Auth ---------------------------------------------------------------------------------

    suspend fun register(email: String, password: String): AuthResponse =
        decode(AuthResponse.serializer(), send("POST", "/auth/register", encode(AuthRequest.serializer(), AuthRequest(email, password)), auth = false))

    suspend fun login(email: String, password: String): AuthResponse =
        decode(AuthResponse.serializer(), send("POST", "/auth/login", encode(AuthRequest.serializer(), AuthRequest(email, password)), auth = false))

    // --- Accounts -----------------------------------------------------------------------------

    suspend fun getAccounts(): List<AccountDto> =
        decode(AccountsResponse.serializer(), send("GET", "/accounts", null)).accounts

    suspend fun createAccount(req: CreateAccountRequest): AccountDto =
        decode(AccountDto.serializer(), send("POST", "/accounts", encode(CreateAccountRequest.serializer(), req)))

    // --- Recents ------------------------------------------------------------------------------

    suspend fun putRecent(recent: RecentDto) {
        send("PUT", "/recents", encode(RecentDto.serializer(), recent))
    }

    suspend fun syncRecents(items: List<RecentDto>): List<RecentDto> =
        decode(RecentsResponse.serializer(), send("POST", "/recents/sync", encode(SyncRequest.serializer(), SyncRequest(items)))).recents

    suspend fun deleteRecent(docId: String) {
        send("DELETE", "/recents?docId=" + URLEncoder.encode(docId, "UTF-8"), null)
    }

    // --- Plumbing -----------------------------------------------------------------------------

    private fun <T> encode(serializer: SerializationStrategy<T>, value: T): String = json.encodeToString(serializer, value)

    private fun <T> decode(serializer: DeserializationStrategy<T>, text: String): T = json.decodeFromString(serializer, text)

    private suspend fun send(method: String, path: String, body: String?, auth: Boolean = true): String =
        withContext(Dispatchers.IO) {
            if (!enabled) throw BackendException(0, "Cloud sync is not configured")
            val builder = HttpRequest.newBuilder(URI.create(AuthStore.baseUrl + path)).timeout(Duration.ofSeconds(30))
            if (auth) AuthStore.token?.let { builder.header("Authorization", "Bearer $it") }
            val publisher = if (body != null) HttpRequest.BodyPublishers.ofString(body) else HttpRequest.BodyPublishers.noBody()
            when (method) {
                "GET" -> builder.GET()
                "DELETE" -> if (body != null) builder.method("DELETE", publisher) else builder.DELETE()
                else -> builder.header("Content-Type", "application/json").method(method, publisher)
            }
            val resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            val text = resp.body().orEmpty()
            if (resp.statusCode() !in 200..299) {
                val message = runCatching { json.decodeFromString(ErrorDto.serializer(), text).error }.getOrNull()
                throw BackendException(resp.statusCode(), message ?: "Request failed (${resp.statusCode()})")
            }
            text
        }
}
