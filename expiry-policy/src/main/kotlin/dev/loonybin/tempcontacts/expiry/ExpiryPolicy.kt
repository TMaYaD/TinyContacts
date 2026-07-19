package dev.loonybin.tempcontacts.expiry

/**
 * Pure, Android-free expiry selection policy.
 *
 * A temp contact stores its expiry as epoch millis in the RawContact's `sync1` column.
 * This object answers the single question the [ExpiryWorker] needs: *given a set of
 * candidate contacts and "now", which ones are expired and must be hard-deleted?*
 *
 * Keeping this logic here (and free of any Android import) is what makes deliverable #3 —
 * unit tests that run headless in a plain JVM sandbox — possible.
 */
object ExpiryPolicy {

    /**
     * A minimal view of a temp contact for expiry decisions: its RawContact id and the
     * expiry instant stored in `sync1`. A `null` [expiryEpochMillis] means "no expiry set"
     * and is treated as never-expiring (defensive: such rows are left untouched, never purged).
     */
    data class Candidate(
        val rawContactId: Long,
        val expiryEpochMillis: Long?,
    )

    /**
     * True when [expiryEpochMillis] is set and strictly before [now].
     *
     * Strictly-before matches the ContentResolver selection used at the SQL layer
     * (`SYNC1 < ?`), so the in-app worker and a raw resolver delete agree to the millisecond.
     */
    fun isExpired(expiryEpochMillis: Long?, now: Long): Boolean =
        expiryEpochMillis != null && expiryEpochMillis < now

    /**
     * Returns the RawContact ids of every candidate that has expired as of [now].
     * Order is preserved from [candidates]; contacts without an expiry are never selected.
     */
    fun selectExpired(candidates: List<Candidate>, now: Long): List<Long> =
        candidates
            .filter { isExpired(it.expiryEpochMillis, now) }
            .map { it.rawContactId }

    /**
     * Remaining lifetime in millis for a contact, or `null` if it never expires.
     * Negative values (already expired) are clamped to 0 so UI can show "expired" cleanly.
     */
    fun remainingMillis(expiryEpochMillis: Long?, now: Long): Long? =
        expiryEpochMillis?.let { (it - now).coerceAtLeast(0L) }
}
