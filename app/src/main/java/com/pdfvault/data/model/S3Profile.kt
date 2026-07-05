package com.pdfvault.data.model

/** A saved S3 account: its connection settings plus a stable id and display name. */
data class S3Profile(
    val id: String,
    val name: String,
    val config: S3Config,
)
