package dev.loonybin.tempcontacts.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import dev.loonybin.tempcontacts.account.AccountContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one and only place in the app that touches [ContactsContract].
 *
 * ## Why raw ContentResolver (and not the vestrel00 library) here
 * Two behaviors are load-bearing for this app and are *not* exposed by the
 * vestrel00/contacts-android convenience API (which, correctly for us, does no syncing):
 *  1. `CALLER_IS_SYNCADAPTER=true` on writes/deletes — without it, deletes are *soft*
 *     (`DELETED=1`, kept pending a sync round-trip that never comes) and inserts are marked
 *     dirty.
 *  2. reading/writing the RawContact `sync1` column, where we stash the expiry epoch millis.
 * So the sync-critical CRUD lives here, on ContentResolver, with the URI decoration
 * centralized in exactly one helper ([syncAdapterUri]).
 */
class ContactsContractRepository(
    context: Context,
) : TempContactsRepository {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    /**
     * The single choke point for sync-adapter URI decoration. EVERY write/delete in this
     * class goes through a URI built here, so the `CALLER_IS_SYNCADAPTER` guarantee is
     * enforced in one place.
     */
    private fun syncAdapterUri(base: Uri): Uri = base.buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(RawContacts.ACCOUNT_NAME, AccountContract.ACCOUNT_NAME)
        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, AccountContract.ACCOUNT_TYPE)
        .build()

    override suspend fun insert(
        name: String,
        phoneNumber: String?,
        expiryEpochMillis: Long,
    ): Long = withContext(Dispatchers.IO) {
        val ops = ArrayList<ContentProviderOperation>()

        // op 0: the RawContact itself, bound to our account with the expiry in sync1.
        ops += ContentProviderOperation.newInsert(syncAdapterUri(RawContacts.CONTENT_URI))
            .withValue(RawContacts.ACCOUNT_TYPE, AccountContract.ACCOUNT_TYPE)
            .withValue(RawContacts.ACCOUNT_NAME, AccountContract.ACCOUNT_NAME)
            .withValue(RawContacts.SYNC1, expiryEpochMillis.toString())
            .build()

        // op 1: structured name, back-referencing op 0's RawContact id.
        ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            .withValue(StructuredName.DISPLAY_NAME, name)
            .build()

        // op 2 (optional): phone number.
        if (!phoneNumber.isNullOrBlank()) {
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, phoneNumber)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        }

        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        // results[0].uri is the inserted RawContact URI; its trailing id is the RawContact id.
        results[0].uri?.let(ContentUris::parseId) ?: -1L
    }

    override suspend fun queryContacts(): List<TempContact> = withContext(Dispatchers.IO) {
        // Step 1: RawContact id + expiry (sync1) for our account, excluding soft-deleted rows.
        val expiryByRawId = LinkedHashMap<Long, Long?>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.SYNC1),
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ? AND ${RawContacts.DELETED} = 0",
            arrayOf(AccountContract.ACCOUNT_TYPE, AccountContract.ACCOUNT_NAME),
            "${RawContacts.SYNC1} ASC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(RawContacts._ID)
            val syncCol = c.getColumnIndexOrThrow(RawContacts.SYNC1)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val expiry = if (c.isNull(syncCol)) null else c.getString(syncCol)?.toLongOrNull()
                expiryByRawId[id] = expiry
            }
        }
        if (expiryByRawId.isEmpty()) return@withContext emptyList()

        // Step 2: names + phones for those RawContacts in a single Data query.
        val names = HashMap<Long, String>()
        val phones = HashMap<Long, String>()
        val ids = expiryByRawId.keys.toList()
        val placeholders = ids.joinToString(",") { "?" }
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1),
            "${Data.RAW_CONTACT_ID} IN ($placeholders) AND ${Data.MIMETYPE} IN (?, ?)",
            (ids.map { it.toString() } + listOf(StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE))
                .toTypedArray(),
            null,
        )?.use { c ->
            val rawCol = c.getColumnIndexOrThrow(Data.RAW_CONTACT_ID)
            val mimeCol = c.getColumnIndexOrThrow(Data.MIMETYPE)
            val dataCol = c.getColumnIndexOrThrow(Data.DATA1)
            while (c.moveToNext()) {
                val rawId = c.getLong(rawCol)
                when (c.getString(mimeCol)) {
                    StructuredName.CONTENT_ITEM_TYPE -> c.getString(dataCol)?.let { names[rawId] = it }
                    Phone.CONTENT_ITEM_TYPE -> c.getString(dataCol)?.let { phones.putIfAbsent(rawId, it) }
                }
            }
        }

        expiryByRawId.map { (rawId, expiry) ->
            TempContact(
                rawContactId = rawId,
                displayName = names[rawId] ?: "(no name)",
                phoneNumber = phones[rawId],
                expiryEpochMillis = expiry,
            )
        }
    }

    override suspend fun deleteExpired(now: Long): Int = withContext(Dispatchers.IO) {
        // Single atomic hard-delete of every expired RawContact for our account.
        // sync1 is a TEXT column, so we CAST to INTEGER for a correct *numeric* comparison
        // (a bare `SYNC1 < ?` string-compares, which only happens to work while all epoch
        // millis have the same digit count). A NULL sync1 casts to NULL and is never matched,
        // so never-expiring rows are left alone — matching ExpiryPolicy.isExpired(null).
        // NOTE: the ExpiryWorker does not call this; it uses the unit-tested ExpiryPolicy to
        // select ids and then deleteByRawContactIds(). This atomic variant is kept for callers
        // that want a one-shot SQL purge.
        resolver.delete(
            syncAdapterUri(RawContacts.CONTENT_URI),
            "${RawContacts.ACCOUNT_TYPE} = ? AND CAST(${RawContacts.SYNC1} AS INTEGER) < ?",
            arrayOf(AccountContract.ACCOUNT_TYPE, now.toString()),
        )
    }

    override suspend fun deleteByRawContactIds(rawContactIds: List<Long>): Int =
        withContext(Dispatchers.IO) {
            if (rawContactIds.isEmpty()) return@withContext 0
            val placeholders = rawContactIds.joinToString(",") { "?" }
            resolver.delete(
                syncAdapterUri(RawContacts.CONTENT_URI),
                "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts._ID} IN ($placeholders)",
                (listOf(AccountContract.ACCOUNT_TYPE) + rawContactIds.map { it.toString() }).toTypedArray(),
            )
        }

    override suspend fun deleteAll(): Int = withContext(Dispatchers.IO) {
        resolver.delete(
            syncAdapterUri(RawContacts.CONTENT_URI),
            "${RawContacts.ACCOUNT_TYPE} = ?",
            arrayOf(AccountContract.ACCOUNT_TYPE),
        )
    }
}
