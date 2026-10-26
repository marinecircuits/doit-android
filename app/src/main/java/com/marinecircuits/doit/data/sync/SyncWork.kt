package com.marinecircuits.doit.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.marinecircuits.doit.container
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (applicationContext.container.syncEngine.sync()) {
        // Give up after a few attempts so later requests replace this one instead of queueing behind its backoff.
        SyncResult.Offline, is SyncResult.ServerError -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        else -> Result.success()
    }
}

object SyncScheduler {
    const val SYNC_NOW = "sync-now"
    const val SYNC_PERIODIC = "sync-periodic"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Runs a sync soon after a local change; a request made during a sync runs after it. */
    fun requestSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES).setConstraints(network).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SYNC_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelPending(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_NOW)
    }
}
