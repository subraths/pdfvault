package com.pdfvault.desktop.data

import com.pdfvault.desktop.model.S3Config
import java.io.File
import java.security.MessageDigest
import java.util.Properties

/** Well-known on-disk locations under the user's home directory. */
object AppStorage {
    val configDir: File = File(System.getProperty("user.home"), ".config/pdfvault").apply { mkdirs() }
    val cacheDir: File = File(configDir, "cache").apply { mkdirs() }

    /** Stable cache path for an object key so a PDF fetched once isn't re-downloaded. */
    fun cacheFileFor(objectKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(objectKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$digest.pdf")
    }
}

/**
 * Persists the active S3 credentials to a local properties file.
 *
 * NOTE: this MVP stores the secret access key in plaintext under ~/.config/pdfvault. A
 * production build should use the OS keyring (libsecret/GNOME Keyring, KWallet). Prefer a
 * least-privilege IAM user for these keys.
 */
class CredentialStore {
    private val file = File(AppStorage.configDir, "credentials.properties")

    val isConfigured: Boolean get() = load() != null

    fun load(): S3Config? {
        if (!file.exists()) return null
        val props = Properties().apply { file.inputStream().use { load(it) } }
        val access = props.getProperty("accessKeyId") ?: return null
        val secret = props.getProperty("secretAccessKey") ?: return null
        val region = props.getProperty("region") ?: return null
        val bucket = props.getProperty("bucket") ?: return null
        return S3Config(access, secret, region, bucket)
    }

    fun save(config: S3Config) {
        val props = Properties().apply {
            setProperty("accessKeyId", config.accessKeyId)
            setProperty("secretAccessKey", config.secretAccessKey)
            setProperty("region", config.region)
            setProperty("bucket", config.bucket)
        }
        file.outputStream().use { props.store(it, "PdfVault credentials (not encrypted)") }
    }

    fun clear() {
        file.delete()
    }
}
