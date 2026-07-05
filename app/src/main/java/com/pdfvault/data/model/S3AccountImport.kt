package com.pdfvault.data.model

/** An S3 account pulled from the backend, to be merged into the local [S3Profile] store. */
data class S3AccountImport(
    val name: String,
    val config: S3Config,
    val active: Boolean,
)
