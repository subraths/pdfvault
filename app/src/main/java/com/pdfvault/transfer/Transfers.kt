package com.pdfvault.transfer

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** One file to move: its S3 [key], the local [path], and [size] in bytes (for progress). */
data class Transfer(val key: String, val path: String, val size: Long)

/**
 * Enqueues background uploads/downloads on WorkManager and exposes their live progress, so
 * transfers keep running when the user leaves the screen and can be cancelled.
 */
@Singleton
class Transfers @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun enqueueUploads(items: List<Transfer>) = enqueue(TransferKind.UPLOAD, items)

    fun enqueueDownloads(items: List<Transfer>) = enqueue(TransferKind.DOWNLOAD, items)

    private fun enqueue(kind: TransferKind, items: List<Transfer>) {
        if (items.isEmpty()) return
        val data = workDataOf(
            TransferKeys.KIND to kind.name,
            TransferKeys.KEYS to items.map { it.key }.toTypedArray(),
            TransferKeys.PATHS to items.map { it.path }.toTypedArray(),
            TransferKeys.SIZES to items.map { it.size }.toLongArray(),
        )
        val request = OneTimeWorkRequestBuilder<TransferWorker>()
            .setInputData(data)
            .addTag(TransferKeys.TAG)
            .build()
        // APPEND_OR_REPLACE chains new batches after any in-flight one under a single name.
        workManager.enqueueUniqueWork("${TransferKeys.TAG}_$kind", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Emits the currently-running transfer's progress, or null when nothing is transferring. */
    val progress: Flow<TransferProgress?> =
        workManager.getWorkInfosByTagFlow(TransferKeys.TAG).map { infos ->
            infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                ?.let { TransferProgress.from(it.progress) }
        }

    fun cancelAll() = workManager.cancelAllWorkByTag(TransferKeys.TAG)
}
