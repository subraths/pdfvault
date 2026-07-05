package com.pdfvault.data.s3

import aws.sdk.kotlin.services.s3.createBucket
import aws.sdk.kotlin.services.s3.getBucketLocation
import aws.sdk.kotlin.services.s3.listBuckets
import aws.sdk.kotlin.services.s3.model.BucketLocationConstraint
import aws.sdk.kotlin.services.s3.model.CreateBucketConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Account-level S3 operations used during setup, before a bucket has been chosen.
 * Each call builds a short-lived client and closes it afterwards.
 */
object S3Setup {

    /** Lists every bucket the credentials can see. Verifies the keys are valid. */
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

    /** Creates a new bucket in the given region. */
    suspend fun createBucket(
        name: String,
        accessKeyId: String,
        secretAccessKey: String,
        region: String,
    ): Unit = withContext(Dispatchers.IO) {
        S3Factory.client(accessKeyId, secretAccessKey, region).use { client ->
            client.createBucket {
                bucket = name
                // us-east-1 must NOT send a location constraint; every other region must.
                if (region != "us-east-1") {
                    createBucketConfiguration = CreateBucketConfiguration {
                        locationConstraint = BucketLocationConstraint.fromValue(region)
                    }
                }
            }
        }
    }

    /**
     * Resolves the actual region a bucket lives in. Object operations must target the
     * bucket's home region or S3 rejects them with a redirect, so we detect it here.
     * Best-effort: falls back to [fallbackRegion] if the lookup fails.
     */
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
                // An empty/null constraint means the legacy us-east-1 region.
                constraint?.takeIf { it.isNotBlank() } ?: "us-east-1"
            }
        }.getOrDefault(fallbackRegion)
    }
}
