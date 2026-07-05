package com.pdfvault.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pdfvault.data.model.S3Config
import com.pdfvault.data.model.S3Profile
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Persists one or more S3 accounts ("profiles") on-device using [EncryptedSharedPreferences]
 * so secret keys are encrypted at rest (AES-256, key held in the Android Keystore). One profile
 * is "current" at a time. A pre-existing single-account install is migrated to a profile.
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy { createPrefs(appContext).also(::migrateLegacy) }

    val isConfigured: Boolean get() = current() != null

    /** Config of the current profile, or null if none. Kept for callers that want just the active one. */
    fun load(): S3Config? = current()

    fun profiles(): List<S3Profile> = ids().mapNotNull(::profile)

    fun currentId(): String? = prefs.getString(KEY_CURRENT, null)?.takeIf { it in ids() }

    fun current(): S3Config? = currentId()?.let { profile(it)?.config }

    /** Adds (or refreshes) a profile for [config], makes it current, and returns its id. */
    fun add(config: S3Config, name: String): String {
        // Reuse an existing profile with the same access key + bucket instead of duplicating.
        val existing = profiles().firstOrNull {
            it.config.accessKeyId == config.accessKeyId && it.config.bucket == config.bucket
        }
        val id = existing?.id ?: newId()
        val ids = (ids() + id).distinct()
        prefs.edit()
            .putString(KEY_IDS, ids.joinToString(","))
            .putString(KEY_CURRENT, id)
            .putString(id + SUF_ACCESS, config.accessKeyId)
            .putString(id + SUF_SECRET, config.secretAccessKey)
            .putString(id + SUF_REGION, config.region)
            .putString(id + SUF_BUCKET, config.bucket)
            .putString(id + SUF_NAME, name.ifBlank { config.bucket })
            .apply()
        return id
    }

    fun setCurrent(id: String) {
        if (id in ids()) prefs.edit().putString(KEY_CURRENT, id).apply()
    }

    fun remove(id: String) {
        val ids = ids() - id
        val editor = prefs.edit()
            .putString(KEY_IDS, ids.joinToString(","))
            .remove(id + SUF_ACCESS)
            .remove(id + SUF_SECRET)
            .remove(id + SUF_REGION)
            .remove(id + SUF_BUCKET)
            .remove(id + SUF_NAME)
        if (prefs.getString(KEY_CURRENT, null) == id) {
            val next = ids.firstOrNull()
            if (next != null) editor.putString(KEY_CURRENT, next) else editor.remove(KEY_CURRENT)
        }
        editor.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun ids(): List<String> =
        prefs.getString(KEY_IDS, null)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    private fun profile(id: String): S3Profile? {
        val access = prefs.getString(id + SUF_ACCESS, null) ?: return null
        val secret = prefs.getString(id + SUF_SECRET, null) ?: return null
        val region = prefs.getString(id + SUF_REGION, null) ?: return null
        val bucket = prefs.getString(id + SUF_BUCKET, null) ?: return null
        val name = prefs.getString(id + SUF_NAME, null) ?: bucket
        return S3Profile(id, name, S3Config(access, secret, region, bucket))
    }

    private fun newId(): String = "p" + UUID.randomUUID().toString().replace("-", "").take(12)

    // Converts an old single-account install (flat keys) into the first profile.
    private fun migrateLegacy(p: SharedPreferences) {
        if (p.getString(KEY_IDS, null) != null) return
        val access = p.getString(LEGACY_ACCESS, null) ?: return
        val secret = p.getString(LEGACY_SECRET, null) ?: return
        val region = p.getString(LEGACY_REGION, null) ?: return
        val bucket = p.getString(LEGACY_BUCKET, null) ?: return
        val id = newId()
        p.edit()
            .putString(KEY_IDS, id)
            .putString(KEY_CURRENT, id)
            .putString(id + SUF_ACCESS, access)
            .putString(id + SUF_SECRET, secret)
            .putString(id + SUF_REGION, region)
            .putString(id + SUF_BUCKET, bucket)
            .putString(id + SUF_NAME, bucket)
            .remove(LEGACY_ACCESS)
            .remove(LEGACY_SECRET)
            .remove(LEGACY_REGION)
            .remove(LEGACY_BUCKET)
            .apply()
    }

    private fun createPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private companion object {
        const val PREFS_NAME = "pdfvault_secure_prefs"
        const val KEY_IDS = "profile_ids"
        const val KEY_CURRENT = "current_profile"
        const val SUF_ACCESS = "_access"
        const val SUF_SECRET = "_secret"
        const val SUF_REGION = "_region"
        const val SUF_BUCKET = "_bucket"
        const val SUF_NAME = "_name"

        // Legacy flat keys from the single-account version, migrated on first read.
        const val LEGACY_ACCESS = "access_key_id"
        const val LEGACY_SECRET = "secret_access_key"
        const val LEGACY_REGION = "region"
        const val LEGACY_BUCKET = "bucket"
    }
}
