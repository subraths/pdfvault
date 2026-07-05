package com.pdfvault.desktop.sync

import com.pdfvault.desktop.data.AuthStore
import com.pdfvault.desktop.data.CredentialStore
import com.pdfvault.desktop.data.ReaderPreferences
import com.pdfvault.desktop.data.RecentItem
import com.pdfvault.desktop.data.RecentsStore
import com.pdfvault.desktop.model.S3Config
import com.pdfvault.desktop.remote.BackendApi
import com.pdfvault.desktop.remote.CreateAccountRequest
import com.pdfvault.desktop.remote.RecentDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Orchestrates backend sync for the desktop: sign-in/out, recents merge (with reading progress),
 * and single-account reconciliation. Everything is a no-op when signed out or unconfigured, so the
 * app stays local-only until the user opts in.
 */
object SyncManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val authState = AuthStore.state
    val enabled: Boolean get() = BackendApi.enabled

    suspend fun register(email: String, password: String) {
        val res = BackendApi.register(email.trim(), password)
        AuthStore.signedIn(res.token, res.user.email)
    }

    suspend fun signIn(email: String, password: String) {
        val res = BackendApi.login(email.trim(), password)
        AuthStore.signedIn(res.token, res.user.email)
    }

    fun signOut() = AuthStore.clear()

    /**
     * Full reconcile. Returns true if a cloud account was imported into the local credential store
     * (so the caller can rebuild its S3 session).
     */
    suspend fun syncAll(): Boolean {
        if (!AuthStore.isSignedIn || !enabled) return false
        val imported = runCatching { syncAccounts() }.getOrDefault(false)
        runCatching { syncRecents() }
        return imported
    }

    /** Pushes the local S3 account (if any/new), and adopts a cloud account when none exists locally. */
    private suspend fun syncAccounts(): Boolean {
        val server = BackendApi.getAccounts()
        val credentials = CredentialStore()
        val local = credentials.load()
        if (local != null && server.none { it.accessKeyId == local.accessKeyId && it.bucket == local.bucket }) {
            runCatching {
                BackendApi.createAccount(
                    CreateAccountRequest(local.bucket, local.region, local.bucket, local.accessKeyId, local.secretAccessKey, active = true),
                )
            }
        }
        if (local == null) {
            val pick = server.firstOrNull { it.active } ?: server.firstOrNull()
            if (pick != null) {
                credentials.save(S3Config(pick.accessKeyId, pick.secretAccessKey, pick.region, pick.bucket))
                return true
            }
        }
        return false
    }

    /** Merges local recents (with reading progress) into the server's and adopts the result. */
    suspend fun syncRecents() {
        val merged = BackendApi.syncRecents(RecentsStore.recents.value.map(::toDto))
        RecentsStore.replaceAll(merged.map { RecentItem(it.docId, it.name, it.openedAt, it.totalPages) })
        merged.forEach { ReaderPreferences.setLastPageFromSync(it.docId, it.lastPage, it.updatedAt) }
    }

    /** Fire-and-forget push of a single recent's current state (call on open and on close). */
    fun pushRecent(docId: String) {
        if (!AuthStore.isSignedIn || !enabled) return
        val item = RecentsStore.recents.value.firstOrNull { it.objectKey == docId } ?: return
        scope.launch { runCatching { BackendApi.putRecent(toDto(item).copy(updatedAt = System.currentTimeMillis())) } }
    }

    private fun toDto(item: RecentItem): RecentDto = RecentDto(
        docId = item.objectKey,
        name = item.name,
        openedAt = item.openedAtMillis,
        totalPages = item.totalPages,
        lastPage = ReaderPreferences.lastPage(item.objectKey),
        updatedAt = maxOf(item.openedAtMillis, ReaderPreferences.lastPageUpdatedAt(item.objectKey)),
    )
}
