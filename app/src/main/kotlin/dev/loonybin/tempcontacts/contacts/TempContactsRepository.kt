package dev.loonybin.tempcontacts.contacts

/**
 * Cross-package boundary over the temp account's contacts. This is the ONLY surface other
 * packages use to reach ContactsContract; the expiry worker and the UI depend on this
 * interface, never on the concrete [ContactsContractRepository].
 *
 * Every implementation must perform its writes/deletes through the sync-adapter URI so that
 * deletes are hard (not soft `DELETED=1`) and inserts are not marked dirty.
 */
interface TempContactsRepository {

    /**
     * Inserts a new temp contact under the dedicated account.
     * @param expiryEpochMillis stored verbatim into the RawContact's `sync1` column.
     * @return the new RawContact id.
     */
    suspend fun insert(name: String, phoneNumber: String?, expiryEpochMillis: Long): Long

    /** Returns every contact currently owned by the temp account, with its `sync1` expiry. */
    suspend fun queryContacts(): List<TempContact>

    /**
     * Hard-deletes contacts of the temp account with `sync1 < now`.
     * @return number of RawContacts removed.
     */
    suspend fun deleteExpired(now: Long): Int

    /**
     * Hard-deletes the given RawContacts (used when the expiry policy selects rows explicitly).
     * @return number of RawContacts removed.
     */
    suspend fun deleteByRawContactIds(rawContactIds: List<Long>): Int

    /**
     * Hard-deletes every contact of the temp account. This is a data-only wipe; prefer
     * removing the account itself (see account/AccountWipe) for the atomic "nuclear" delete.
     * @return number of RawContacts removed.
     */
    suspend fun deleteAll(): Int
}
