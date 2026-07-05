package com.pdfvault.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pdfvault.S3SessionManager
import com.pdfvault.data.BackupPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Periodically (and on demand) uploads new PDFs from a user-picked device folder to S3. A file
 * is considered "new" if an object with its destination key doesn't already exist, so re-runs
 * are idempotent. Runs best-effort; failures are retried on the next scheduled pass.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun session(): S3SessionManager
        fun backupPreferences(): BackupPreferences
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val settings = deps.backupPreferences().current()
        val treeUri = settings.treeUri
        if (!settings.enabled || treeUri.isNullOrBlank()) return@withContext Result.success()
        val repository = deps.session().repository ?: return@withContext Result.success()

        val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(treeUri))
            ?: return@withContext Result.success()
        val prefix = settings.destPrefix.let { if (it.isBlank() || it.endsWith("/")) it else "$it/" }

        val pdfs = tree.listFiles().filter { doc ->
            doc.isFile && (doc.name?.endsWith(".pdf", ignoreCase = true) == true)
        }
        for (doc in pdfs) {
            if (isStopped) break
            val name = doc.name ?: continue
            val destKey = prefix + name
            // Skip if S3 already has it (idempotent backup).
            if (repository.objectETag(destKey) != null) continue
            runCatching {
                val temp = File.createTempFile("backup_", ".pdf", applicationContext.cacheDir)
                try {
                    applicationContext.contentResolver.openInputStream(doc.uri)?.use { input ->
                        temp.outputStream().use { input.copyTo(it) }
                    } ?: error("Unable to read ${doc.name}")
                    repository.upload(destKey, temp)
                } finally {
                    temp.delete()
                }
            }
        }
        Result.success()
    }
}

/** Schedules the periodic auto-backup and offers an on-demand "back up now". */
@Singleton
class AutoBackupManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Enables or cancels the recurring backup based on [enabled]. */
    fun schedule(enabled: Boolean) {
        if (!enabled) {
            workManager.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun backupNow() {
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val TAG = "pdfvault_backup"
        const val PERIODIC = "pdfvault_auto_backup"
        const val ONE_SHOT = "pdfvault_backup_now"
    }
}
