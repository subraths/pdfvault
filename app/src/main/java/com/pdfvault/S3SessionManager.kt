package com.pdfvault

import android.content.Context
import com.pdfvault.data.CredentialStore
import com.pdfvault.data.model.S3AccountImport
import com.pdfvault.data.model.S3Config
import com.pdfvault.data.model.S3Profile
import com.pdfvault.data.s3.S3Repository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide holder for the active S3 session. Owns the current [S3Repository] and rebuilds
 * it whenever the user (re)configures their credentials. Provided as a Hilt [Singleton].
 */
@Singleton
class S3SessionManager @Inject constructor(
    @ApplicationContext context: Context,
    val credentialStore: CredentialStore,
) {
    private val appContext = context.applicationContext

    /** Volatile cache for staging pending uploads. */
    val cacheDir: File = appContext.cacheDir

    /**
     * Persistent on-device store for downloaded PDFs. Lives under filesDir (not cacheDir)
     * so a PDF fetched once stays available and is never re-downloaded from S3.
     */
    val pdfCacheDir: File = File(appContext.filesDir, "pdf_cache")

    val isConfigured: Boolean get() = credentialStore.isConfigured

    @Volatile
    private var cachedRepository: S3Repository? = null

    /** The active profile id; changes when the user adds/switches/removes accounts. */
    private val _activeProfile = MutableStateFlow(credentialStore.currentId())
    val activeProfile: StateFlow<String?> = _activeProfile.asStateFlow()

    /** All saved accounts; updates when profiles are added/removed. */
    private val _profiles = MutableStateFlow(credentialStore.profiles())
    val profiles: StateFlow<List<S3Profile>> = _profiles.asStateFlow()

    /** The active repository, lazily built from the current profile, or null if unconfigured. */
    @get:Synchronized
    val repository: S3Repository?
        get() {
            cachedRepository?.let { return it }
            val config = credentialStore.load() ?: return null
            return S3Repository(config).also { cachedRepository = it }
        }

    /** Adds (or refreshes) the account for [config], makes it current, and rebuilds the session. */
    @Synchronized
    fun configure(config: S3Config, name: String = config.bucket) {
        credentialStore.add(config, name)
        cachedRepository?.close()
        cachedRepository = S3Repository(config)
        publishProfiles()
    }

    /**
     * Merges [accounts] pulled from the backend into the local profile store (de-duplicating by
     * access key + bucket), preserving/settling the active profile, then rebuilds the session.
     */
    @Synchronized
    fun importAccounts(accounts: List<S3AccountImport>) {
        if (accounts.isEmpty()) return
        val previousCurrent = credentialStore.currentId()
        var activeId: String? = null
        for (account in accounts) {
            // add() de-dupes by access key + bucket and marks the added one current.
            val id = credentialStore.add(account.config, account.name)
            if (account.active) activeId = id
        }
        val target = activeId ?: previousCurrent ?: credentialStore.profiles().firstOrNull()?.id
        target?.let { credentialStore.setCurrent(it) }
        cachedRepository?.close()
        cachedRepository = credentialStore.load()?.let { S3Repository(it) }
        publishProfiles()
    }

    /** Switches to a saved account by id and rebuilds the session. */
    @Synchronized
    fun switchTo(profileId: String) {
        if (profileId == credentialStore.currentId()) return
        credentialStore.setCurrent(profileId)
        cachedRepository?.close()
        cachedRepository = credentialStore.load()?.let { S3Repository(it) }
        publishProfiles()
    }

    /** Removes a saved account; if it was current, activates another (or none). */
    @Synchronized
    fun removeProfile(profileId: String) {
        val wasCurrent = profileId == credentialStore.currentId()
        credentialStore.remove(profileId)
        if (wasCurrent) {
            cachedRepository?.close()
            cachedRepository = credentialStore.load()?.let { S3Repository(it) }
        }
        publishProfiles()
    }

    @Synchronized
    fun reset() {
        credentialStore.clear()
        cachedRepository?.close()
        cachedRepository = null
        publishProfiles()
    }

    private fun publishProfiles() {
        _profiles.value = credentialStore.profiles()
        _activeProfile.value = credentialStore.currentId()
    }

    // --- PDF cache addressing --------------------------------------------------------------

    /** SHA-256 of the object key; the stable, filesystem-safe stem for its cache files. */
    private fun keyDigest(objectKey: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(objectKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * Cache path for a specific version of an object: "<digest>[.<etag>].pdf". Including the
     * ETag means a changed S3 object downloads fresh instead of reusing a stale local copy.
     */
    fun cacheFileFor(objectKey: String, eTag: String?): File {
        val tag = eTag?.trim('"')?.filter { it.isLetterOrDigit() }?.take(32)?.takeIf { it.isNotEmpty() }
        val suffix = if (tag == null) "" else ".$tag"
        return File(pdfCacheDir, "${keyDigest(objectKey)}$suffix.pdf")
    }

    /** The newest complete cached file for [objectKey] regardless of version (offline fallback). */
    fun newestCachedFor(objectKey: String): File? {
        val stem = keyDigest(objectKey)
        return pdfCacheDir.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.name.startsWith(stem) && it.name.endsWith(".pdf") }
            ?.maxByOrNull { it.lastModified() }
    }

    /** Best local copy for [objectKey]: the exact-ETag file if present, else newest cached, else null. */
    fun cachedPdf(objectKey: String, eTag: String?): File? {
        val exact = cacheFileFor(objectKey, eTag)
        if (exact.exists() && exact.length() > 0L) return exact
        return newestCachedFor(objectKey)
    }

    // --- PDF cache maintenance -------------------------------------------------------------

    fun pdfCacheSizeBytes(): Long =
        pdfCacheDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun clearPdfCache() {
        pdfCacheDir.listFiles()?.forEach { it.delete() }
    }

    /** Evicts the least-recently-modified cached PDFs until under [maxBytes], keeping [keep]. */
    fun prunePdfCache(maxBytes: Long, keep: File? = null) {
        val files = pdfCacheDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            if (file == keep) continue
            val len = file.length()
            if (file.delete()) total -= len
        }
    }
}
