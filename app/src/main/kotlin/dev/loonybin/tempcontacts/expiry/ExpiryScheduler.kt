package dev.loonybin.tempcontacts.expiry

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Cross-package boundary for registering the periodic purge. The UI/app wire this up; it
 * knows nothing about how contacts are stored or how the account is authenticated.
 */
interface ExpiryScheduler {
    /** Registers the periodic expiry sweep (idempotent — keeps the existing schedule). */
    fun schedulePeriodicSweep()

    /** Cancels the periodic expiry sweep. */
    fun cancel()
}

/**
 * [ExpiryScheduler] backed by WorkManager. 15 minutes is WorkManager's minimum period; the
 * exact cadence is not critical because expiry is also enforced lazily wherever contacts are
 * read, and the worker deletes everything already past due each run.
 */
class WorkManagerExpiryScheduler(
    context: Context,
) : ExpiryScheduler {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedulePeriodicSweep() {
        val request = PeriodicWorkRequestBuilder<ExpiryWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES,
        ).build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private companion object {
        const val UNIQUE_WORK_NAME = "temp-contacts-expiry-sweep"
    }
}
