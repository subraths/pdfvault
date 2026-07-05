package com.pdfvault.data.s3

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import com.pdfvault.data.model.S3Config

/** Builds configured [S3Client] instances from user credentials. */
object S3Factory {

    fun client(accessKeyId: String, secretAccessKey: String, region: String): S3Client =
        S3Client {
            this.region = region
            credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = accessKeyId
                this.secretAccessKey = secretAccessKey
            }
        }

    fun client(config: S3Config): S3Client =
        client(config.accessKeyId, config.secretAccessKey, config.region)
}
