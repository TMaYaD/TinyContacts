package dev.loonybin.tempcontacts.expiry

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.loonybin.tempcontacts.TempContactsApp
import dev.loonybin.tempcontacts.contacts.TempContactsRepository

/**
 * Periodic worker that purges expired temp contacts.
 *
 * The decision of *which* rows are expired is made by the pure [ExpiryPolicy] (unit-tested,
 * Android-free) — the worker only supplies "now" and the candidate list, then asks the
 * repository to hard-delete the selected rows. This keeps the sync1<now rule in one tested
 * place and out of the Android plumbing.
 */
class ExpiryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val repository: TempContactsRepository =
        (appContext.applicationContext as TempContactsApp).container.contactsRepository

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()

        return try {
            val candidates = repository.queryContacts().map {
                ExpiryPolicy.Candidate(rawContactId = it.rawContactId, expiryEpochMillis = it.expiryEpochMillis)
            }
            val expired = ExpiryPolicy.selectExpired(candidates, now)

            if (expired.isNotEmpty()) {
                repository.deleteByRawContactIds(expired)
            }
            Result.success()
        } catch (e: SecurityException) {
            // Contacts permission not yet granted — nothing to purge. Try again next cycle.
            Result.success()
        }
    }
}
