package com.pdfvault.sync

import com.pdfvault.S3SessionManager
import com.pdfvault.data.ReaderPreferences
import com.pdfvault.data.RecentItem
import com.pdfvault.data.RecentsStore
import com.pdfvault.data.auth.AuthStore
import com.pdfvault.data.model.S3AccountImport
import com.pdfvault.data.model.S3Config
import com.pdfvault.data.remote.BackendApi
import com.pdfvault.data.remote.CreateAccountRequest
import com.pdfvault.data.remote.RecentDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates backend sync: sign-in/out plus two-way reconciliation of S3 accounts and recents
 * (including reading progress) so the phone and desktop converge. When signed out or the backend
 * isn't configured, everything stays local — sync is purely additive.
 */
@Singleton
class SyncManager @Inject constructor(
    private val api: BackendApi,
    private val authStore: AuthStore,
    private val session: S3SessionManager,
    private val recents: RecentsStore,
    private val readerPrefs: ReaderPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val authState = authStore.state
    val enabled: Boolean get() = api.enabled

    /** Creates an account, stores the token, and performs an initial sync. */
    suspend fun register(email: String, password: String) {
        val res = api.register(email.trim(), password)
        authStore.save(res.token, res.user.email)
        syncAll()
    }

    /** Signs in, stores the token, and performs an initial sync. */
    suspend fun signIn(email: String, password: String) {
        val res = api.login(email.trim(), password)
        authStore.save(res.token, res.user.email)
        syncAll()
    }

    /** Signs out locally; keeps local accounts + recents intact. */
    fun signOut() = authStore.clear()

    /** Full reconcile: accounts first (so recents' S3 keys make sense), then recents. Safe to call anytime. */
    suspend fun syncAll() {
        if (!authStore.isSignedIn || !enabled) return
        runCatching { syncAccounts() }
        runCatching { syncRecents() }
    }

    /** Two-way account sync: push local-only profiles, then adopt the server's full set. */
    suspend fun syncAccounts() {
        val server = api.getAccounts()
        val serverKeys = server.map { it.accessKeyId to it.bucket }.toSet()
        val activeId = session.activeProfile.value
        for (profile in session.credentialStore.profiles()) {
            val key = profile.config.accessKeyId to profile.config.bucket
            if (key in serverKeys) continue
            runCatching {
                api.createAccount(
                    CreateAccountRequest(
                        name = profile.name,
                        region = profile.config.region,
                        bucket = profile.config.bucket,
                        accessKeyId = profile.config.accessKeyId,
                        secretAccessKey = profile.config.secretAccessKey,
                        active = profile.id == activeId,
                    ),
                )
            }
        }
        val merged = api.getAccounts()
        session.importAccounts(
            merged.map { S3AccountImport(it.name, S3Config(it.accessKeyId, it.secretAccessKey, it.region, it.bucket), it.active) },
        )
    }

    /** Merges local recents (with reading progress) into the server's and adopts the result. */
    suspend fun syncRecents() {
        val merged = api.syncRecents(recents.recents.value.map(::toDto))
        recents.replaceAll(merged.map { RecentItem(it.docId, it.name, it.openedAt, it.totalPages) })
        merged.forEach { readerPrefs.setLastPageFromSync(it.docId, it.lastPage, it.updatedAt) }
    }

    /** Fire-and-forget push of a single recent's current state (call on open and on page change). */
    fun pushRecent(docId: String) {
        if (!authStore.isSignedIn || !enabled) return
        val item = recents.recents.value.firstOrNull { it.objectKey == docId } ?: return
        scope.launch { runCatching { api.putRecent(toDto(item).copy(updatedAt = System.currentTimeMillis())) } }
    }

    /**
     * Fire-and-forget remote delete (tombstone) so removing a recent here removes it on every
     * device — other clients drop it on their next sync instead of re-uploading it.
     */
    fun deleteRecentRemote(docId: String) {
        if (!authStore.isSignedIn || !enabled) return
        scope.launch { runCatching { api.deleteRecent(docId) } }
    }

    private fun toDto(item: RecentItem): RecentDto = RecentDto(
        docId = item.objectKey,
        name = item.name,
        openedAt = item.openedAtMillis,
        totalPages = item.totalPages,
        lastPage = readerPrefs.lastPage(item.objectKey),
        updatedAt = maxOf(item.openedAtMillis, readerPrefs.lastPageUpdatedAt(item.objectKey)),
    )
}
