package com.pdfvault.data.s3

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.copyObject
import aws.sdk.kotlin.services.s3.deleteObject
import aws.sdk.kotlin.services.s3.deleteObjects
import aws.sdk.kotlin.services.s3.headObject
import aws.sdk.kotlin.services.s3.listObjectsV2
import aws.sdk.kotlin.services.s3.model.Delete
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ObjectIdentifier
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.putObject
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.fromFile
import aws.smithy.kotlin.runtime.content.writeToFile
import com.pdfvault.data.model.S3Config
import com.pdfvault.data.model.S3Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.net.URLEncoder
import kotlin.time.Duration

/**
 * Bucket-scoped S3 operations backing the file browser and viewer. One instance wraps
 * a single [S3Client]; remember to [close] it when the active configuration changes.
 */
class S3Repository(config: S3Config) : Closeable {

    val bucket: String = config.bucket
    private val client: S3Client = S3Factory.client(config)

    /**
     * Lists the immediate children of [prefix] (use "" for the bucket root), splitting
     * results into folders (common prefixes) and files via the "/" delimiter.
     */
    suspend fun listChildren(prefix: String): List<S3Item> = withContext(Dispatchers.IO) {
        val folders = mutableListOf<S3Item.Folder>()
        val files = mutableListOf<S3Item.File>()
        var token: String? = null
        do {
            val resp = client.listObjectsV2 {
                bucket = this@S3Repository.bucket
                this.prefix = prefix
                delimiter = "/"
                continuationToken = token
            }
            resp.commonPrefixes?.forEach { cp ->
                val p = cp.prefix ?: return@forEach
                val name = p.removePrefix(prefix).trimEnd('/')
                if (name.isNotEmpty()) folders += S3Item.Folder(name = name, key = p)
            }
            resp.contents?.forEach { obj ->
                val key = obj.key ?: return@forEach
                // Skip the folder placeholder object and anything in a deeper level.
                if (key == prefix || key.endsWith("/")) return@forEach
                val name = key.removePrefix(prefix)
                if (name.isEmpty() || name.contains("/")) return@forEach
                files += S3Item.File(
                    name = name,
                    key = key,
                    size = obj.size ?: 0L,
                    lastModifiedEpochSeconds = obj.lastModified?.epochSeconds ?: 0L,
                    eTag = obj.eTag,
                )
            }
            token = if (resp.isTruncated == true) resp.nextContinuationToken else null
        } while (token != null)

        folders.sortedBy { it.name.lowercase() } + files.sortedBy { it.name.lowercase() }
    }

    /** Lists every non-folder object in the bucket as File items (for bucket-wide search). */
    suspend fun listAllFiles(): List<S3Item.File> = withContext(Dispatchers.IO) {
        val files = mutableListOf<S3Item.File>()
        var token: String? = null
        do {
            val resp = client.listObjectsV2 {
                bucket = this@S3Repository.bucket
                continuationToken = token
            }
            resp.contents?.forEach { obj ->
                val key = obj.key ?: return@forEach
                if (key.endsWith("/")) return@forEach
                files += S3Item.File(
                    name = key.substringAfterLast('/'),
                    key = key,
                    size = obj.size ?: 0L,
                    lastModifiedEpochSeconds = obj.lastModified?.epochSeconds ?: 0L,
                    eTag = obj.eTag,
                )
            }
            token = if (resp.isTruncated == true) resp.nextContinuationToken else null
        } while (token != null)
        files
    }

    /** A time-limited public URL for [key] that anyone can use to download it (no credentials). */
    suspend fun presignedUrl(key: String, duration: Duration): String = withContext(Dispatchers.IO) {
        client.presignGetObject(
            GetObjectRequest {
                bucket = this@S3Repository.bucket
                this.key = key
            },
            duration,
        ).url.toString()
    }

    /** Streams the object at [key] into [dest] and returns it. */
    suspend fun download(key: String, dest: File): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val request = GetObjectRequest {
            bucket = this@S3Repository.bucket
            this.key = key
        }
        client.getObject(request) { resp ->
            resp.body?.writeToFile(dest)
        }
        dest
    }

    /** Uploads a local [file] to [key], overwriting any existing object. */
    suspend fun upload(key: String, file: File): Unit = withContext(Dispatchers.IO) {
        client.putObject {
            bucket = this@S3Repository.bucket
            this.key = key
            body = ByteStream.fromFile(file)
            contentType = "application/pdf"
        }
    }

    /** Creates an empty "folder" by storing a zero-byte object whose key ends in "/". */
    suspend fun createFolder(prefix: String): Unit = withContext(Dispatchers.IO) {
        val normalized = if (prefix.endsWith("/")) prefix else "$prefix/"
        client.putObject {
            bucket = this@S3Repository.bucket
            key = normalized
            body = ByteStream.fromBytes(ByteArray(0))
        }
    }

    /** Deletes a single object (or folder placeholder) at [key]. */
    suspend fun delete(key: String): Unit = withContext(Dispatchers.IO) {
        client.deleteObject {
            bucket = this@S3Repository.bucket
            this.key = key
        }
    }

    /** Deletes [item]: a file removes one object; a folder removes everything under its prefix. */
    suspend fun delete(item: S3Item) {
        when (item) {
            is S3Item.File -> delete(item.key)
            is S3Item.Folder -> deletePrefix(item.key)
        }
    }

    /** Deletes every object under [prefix] using batched DeleteObjects (up to 1000/call). */
    suspend fun deletePrefix(prefix: String): Unit = withContext(Dispatchers.IO) {
        val root = if (prefix.endsWith("/")) prefix else "$prefix/"
        deleteKeys(listAllKeys(root))
    }

    /** Best-effort current ETag of [key] via HeadObject; null if it can't be read (e.g. offline). */
    suspend fun objectETag(key: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.headObject {
                bucket = this@S3Repository.bucket
                this.key = key
            }.eTag
        }.getOrNull()
    }

    private suspend fun deleteKeys(keys: List<String>) {
        keys.chunked(1000).forEach { chunk ->
            client.deleteObjects {
                bucket = this@S3Repository.bucket
                delete = Delete { objects = chunk.map { ObjectIdentifier { key = it } } }
            }
        }
    }

    private suspend fun listAllKeys(prefix: String): List<String> {
        val keys = mutableListOf<String>()
        var token: String? = null
        do {
            val resp = client.listObjectsV2 {
                bucket = this@S3Repository.bucket
                this.prefix = prefix
                continuationToken = token
            }
            resp.contents?.forEach { obj -> obj.key?.let(keys::add) }
            token = if (resp.isTruncated == true) resp.nextContinuationToken else null
        } while (token != null)
        return keys
    }

    /**
     * Moves/renames [item] to [newKey] (a full object key for a file, or a prefix ending
     * in "/" for a folder). S3 has no move op, so this copies then deletes. Folders move
     * every object under their prefix.
     */
    suspend fun move(item: S3Item, newKey: String) {
        when (item) {
            is S3Item.File -> {
                copy(item.key, newKey)
                delete(item.key)
            }
            is S3Item.Folder -> moveFolder(item.key, newKey)
        }
    }

    private suspend fun moveFolder(sourcePrefix: String, destPrefix: String) = withContext(Dispatchers.IO) {
        val src = if (sourcePrefix.endsWith("/")) sourcePrefix else "$sourcePrefix/"
        val dst = if (destPrefix.endsWith("/")) destPrefix else "$destPrefix/"

        val keys = listAllKeys(src)
        // Copy every descendant to the new prefix first, then batch-delete the originals.
        for (key in keys) copy(key, dst + key.removePrefix(src))
        deleteKeys(keys)
    }

    private suspend fun copy(sourceKey: String, destKey: String): Unit = withContext(Dispatchers.IO) {
        client.copyObject {
            bucket = this@S3Repository.bucket
            copySource = encodeCopySource(this@S3Repository.bucket, sourceKey)
            key = destKey
        }
    }

    // The x-amz-copy-source value must be URL-encoded (per path segment, keeping "/").
    private fun encodeCopySource(bucket: String, key: String): String {
        val encodedKey = key.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
        return "$bucket/$encodedKey"
    }

    override fun close() {
        client.close()
    }
}
