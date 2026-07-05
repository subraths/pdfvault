package com.pdfvault.desktop.s3

import aws.sdk.kotlin.services.s3.createBucket
import aws.sdk.kotlin.services.s3.getBucketLocation
import aws.sdk.kotlin.services.s3.listBuckets
import aws.sdk.kotlin.services.s3.model.BucketLocationConstraint
import aws.sdk.kotlin.services.s3.model.CreateBucketConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Account-level S3 operations used during setup, before a bucket has been chosen. */
object S3Setup {

    suspend fun listBuckets(
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
    ): List<String> = withContext(Dispatchers.IO) {
        S3Factory.client(accessKeyId, secretAccessKey, region).use { client ->
            client.listBuckets { }
                .buckets
                ?.mapNotNull { it.name }
                ?.sorted()
                .orEmpty()
        }
    }

    suspend fun createBucket(
        name: String,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
    ): Unit = withContext(Dispatchers.IO) {
        S3Factory.client(accessKeyId, secretAccessKey, region).use { client ->
            client.createBucket {
                bucket = name
                if (region != "us-east-1") {
                    createBucketConfiguration = CreateBucketConfiguration {
                        locationConstraint = BucketLocationConstraint.fromValue(region)
                    }
                }
            }
        }
    }

    /** Resolves the actual region a bucket lives in; falls back to [fallbackRegion]. */
    suspend fun resolveBucketRegion(
        bucket: String,
        accessKeyId: String,
        secretAccessKey: String,
        fallbackRegion: String,
    ): String = withContext(Dispatchers.IO) {
        runCatching {
            S3Factory.client(accessKeyId, secretAccessKey, fallbackRegion).use { client ->
                val constraint = client.getBucketLocation { this.bucket = bucket }
                    .locationConstraint
                    ?.value
                constraint?.takeIf { it.isNotBlank() } ?: "us-east-1"
            }
        }.getOrDefault(fallbackRegion)
    }
}
