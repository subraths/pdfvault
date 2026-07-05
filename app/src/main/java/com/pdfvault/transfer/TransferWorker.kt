package com.pdfvault.transfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.pdfvault.S3SessionManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import java.io.File

enum class TransferKind { UPLOAD, DOWNLOAD }

/** WorkManager keys for a transfer request and its live progress. */
object TransferKeys {
    const val KIND = "kind"
    const val KEYS = "keys"
    const val PATHS = "paths"
    const val SIZES = "sizes"

    const val P_KIND = "p_kind"
    const val P_NAME = "p_name"
    const val P_DONE = "p_done"
    const val P_TOTAL = "p_total"
    const val P_PCT = "p_pct"

    const val TAG = "pdfvault_transfer"
}

/**
 * Background upload/download that survives leaving the screen (WorkManager). Uploads report
 * file-by-file progress; downloads also report byte-percent (estimated from the temp file's
 * growth against the known object size). Cooperative-cancellable via WorkManager.
 */
class TransferWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun session(): S3SessionManager
    }

    override suspend fun doWork(): Result {
        val kind = runCatching { TransferKind.valueOf(inputData.getString(TransferKeys.KIND).orEmpty()) }
            .getOrNull() ?: return Result.failure()
        val keys = inputData.getStringArray(TransferKeys.KEYS) ?: return Result.failure()
        val paths = inputData.getStringArray(TransferKeys.PATHS) ?: return Result.failure()
        val sizes = inputData.getLongArray(TransferKeys.SIZES) ?: LongArray(keys.size)
        if (keys.size != paths.size) return Result.failure()

        val session = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).session()
        val repository = session.repository ?: return Result.failure()

        val total = keys.size
        var failures = 0
        for (i in keys.indices) {
            if (isStopped) return Result.failure()
            val key = keys[i]
            val name = key.substringAfterLast('/').ifBlank { key }
            report(kind, name, i, total, 0f)
            runCatching {
                when (kind) {
                    TransferKind.UPLOAD -> {
                        val file = File(paths[i])
                        repository.upload(key, file)
                        file.delete()
                    }

                    TransferKind.DOWNLOAD -> {
                        downloadWithProgress(repository, key, File(paths[i]), sizes[i]) { pct ->
                            report(kind, name, i, total, pct)
                        }
                    }
                }
            }.onFailure { failures++ }
        }
        return if (failures == 0) Result.success() else Result.failure()
    }

    private suspend fun report(kind: TransferKind, name: String, done: Int, total: Int, pct: Float) {
        setProgress(
            workDataOf(
                TransferKeys.P_KIND to kind.name,
                TransferKeys.P_NAME to name,
                TransferKeys.P_DONE to done,
                TransferKeys.P_TOTAL to total,
                TransferKeys.P_PCT to pct,
            ),
        )
    }

    private suspend fun downloadWithProgress(
        repository: com.pdfvault.data.s3.S3Repository,
        key: String,
        dest: File,
        totalBytes: Long,
        onPct: suspend (Float) -> Unit,
    ) {
        dest.parentFile?.mkdirs()
        val tmp = File.createTempFile("dl_", ".pdf", dest.parentFile)
        try {
            coroutineScope {
                val monitor = launch {
                    while (isActive) {
                        if (totalBytes > 0) onPct((tmp.length().toFloat() / totalBytes).coerceIn(0f, 1f))
                        delay(250)
                    }
                }
                try {
                    repository.download(key, tmp)
                } finally {
                    monitor.cancel()
                }
            }
            onPct(1f)
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }
}

/** Snapshot of an in-flight transfer, parsed from a [Data] progress payload. */
data class TransferProgress(
    val kind: TransferKind,
    val name: String,
    val done: Int,
    val total: Int,
    val pct: Float,
) {
    companion object {
        fun from(data: Data): TransferProgress? {
            val kind = runCatching { TransferKind.valueOf(data.getString(TransferKeys.P_KIND).orEmpty()) }
                .getOrNull() ?: return null
            return TransferProgress(
                kind = kind,
                name = data.getString(TransferKeys.P_NAME).orEmpty(),
                done = data.getInt(TransferKeys.P_DONE, 0),
                total = data.getInt(TransferKeys.P_TOTAL, 0),
                pct = data.getFloat(TransferKeys.P_PCT, 0f),
            )
        }
    }
}
