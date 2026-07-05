package com.pdfvault.data.model

/**
 * A node shown in the file browser. S3 has no real folders; "folders" are derived
 * from common prefixes when listing with a "/" delimiter.
 */
sealed interface S3Item {
    /** Last path segment shown to the user. */
    val name: String

    /** Full S3 key (files) or prefix ending in "/" (folders). */
    val key: String

    data class Folder(
        override val name: String,
        override val key: String,
    ) : S3Item

    data class File(
        override val name: String,
        override val key: String,
        val size: Long,
        val lastModifiedEpochSeconds: Long,
        val eTag: String? = null,
    ) : S3Item {
        val isPdf: Boolean get() = name.endsWith(".pdf", ignoreCase = true)
    }
}
