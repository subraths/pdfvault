package com.pdfvault.data.remote

import com.pdfvault.BuildConfig
import com.pdfvault.data.auth.AuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown for any non-2xx backend response (or when the backend isn't configured). */
class BackendException(val status: Int, message: String) : Exception(message)

@Serializable
private data class ErrorDto(val error: String? = null)

/**
 * Thin HTTP client for the PdfVault backend, using OkHttp + kotlinx-serialization. Each call runs
 * on IO and attaches the bearer token (from [AuthStore]) when [auth] is required. The base URL comes
 * from `BuildConfig.BACKEND_BASE_URL`; when blank, [enabled] is false and calls fail fast.
 */
@Singleton
class BackendApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val authStore: AuthStore,
) {
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL

    val enabled: Boolean get() = baseUrl.isNotBlank()

    // --- Auth ---------------------------------------------------------------------------------

    suspend fun register(email: String, password: String): AuthResponse =
        decode(AuthResponse.serializer(), send("POST", "/auth/register", encode(AuthRequest.serializer(), AuthRequest(email, password)), auth = false))

    suspend fun login(email: String, password: String): AuthResponse =
        decode(AuthResponse.serializer(), send("POST", "/auth/login", encode(AuthRequest.serializer(), AuthRequest(email, password)), auth = false))

    suspend fun me(): UserDto = decode(UserDto.serializer(), send("GET", "/me", null))

    // --- Accounts (multi-account S3 profiles) -------------------------------------------------

    suspend fun getAccounts(): List<AccountDto> =
        decode(AccountsResponse.serializer(), send("GET", "/accounts", null)).accounts

    suspend fun createAccount(req: CreateAccountRequest): AccountDto =
        decode(AccountDto.serializer(), send("POST", "/accounts", encode(CreateAccountRequest.serializer(), req)))

    suspend fun updateAccount(id: String, req: UpdateAccountRequest): AccountDto =
        decode(AccountDto.serializer(), send("PATCH", "/accounts/$id", encode(UpdateAccountRequest.serializer(), req)))

    suspend fun deleteAccount(id: String) {
        send("DELETE", "/accounts/$id", null)
    }

    // --- Recents (synced) ---------------------------------------------------------------------

    suspend fun getRecents(): List<RecentDto> =
        decode(RecentsResponse.serializer(), send("GET", "/recents", null)).recents

    suspend fun putRecent(recent: RecentDto) {
        send("PUT", "/recents", encode(RecentDto.serializer(), recent))
    }

    suspend fun syncRecents(items: List<RecentDto>): List<RecentDto> =
        decode(RecentsResponse.serializer(), send("POST", "/recents/sync", encode(SyncRequest.serializer(), SyncRequest(items)))).recents

    suspend fun deleteRecent(docId: String) {
        send("DELETE", "/recents?docId=" + URLEncoder.encode(docId, "UTF-8"), null)
    }

    // --- Plumbing -----------------------------------------------------------------------------

    private fun <T> encode(serializer: kotlinx.serialization.SerializationStrategy<T>, value: T): String =
        json.encodeToString(serializer, value)

    private fun <T> decode(serializer: kotlinx.serialization.DeserializationStrategy<T>, text: String): T =
        json.decodeFromString(serializer, text)

    private suspend fun send(method: String, path: String, bodyJson: String?, auth: Boolean = true): String =
        withContext(Dispatchers.IO) {
            if (!enabled) throw BackendException(0, "Cloud sync is not configured")
            val body = bodyJson?.toRequestBody(JSON_MEDIA)
            val builder = Request.Builder().url(baseUrl + path)
            if (auth) authStore.token?.let { builder.header("Authorization", "Bearer $it") }
            when (method) {
                "GET" -> builder.get()
                "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
                "POST" -> builder.post(body ?: EMPTY_BODY)
                "PUT" -> builder.put(body ?: EMPTY_BODY)
                "PATCH" -> builder.patch(body ?: EMPTY_BODY)
                else -> error("Unsupported method $method")
            }
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val message = runCatching { json.decodeFromString(ErrorDto.serializer(), text).error }.getOrNull()
                    throw BackendException(resp.code, message ?: "Request failed (${resp.code})")
                }
                text
            }
        }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val EMPTY_BODY = "".toRequestBody("application/json".toMediaType())
    }
}
