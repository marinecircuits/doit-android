package com.marinecircuits.doit.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun localChangeEnqueuesSyncNowThatNeedsANetwork() {
        SyncScheduler.requestSync(context)

        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncScheduler.SYNC_NOW).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun periodicSyncIsScheduledOnce() {
        SyncScheduler.schedulePeriodic(context)
        SyncScheduler.schedulePeriodic(context)

        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(SyncScheduler.SYNC_PERIODIC).get()
        assertEquals(1, work.size)
        assertEquals(NetworkType.CONNECTED, work.single().constraints.requiredNetworkType)
    }
}
