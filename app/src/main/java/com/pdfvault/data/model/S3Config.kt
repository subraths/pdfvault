package com.pdfvault.data.model

/** Connection settings entered by the user during setup. */
data class S3Config(
    val accessKeyId: String,
    val secretAccessKey: String,
    val region: String,
    val bucket: String,
)
