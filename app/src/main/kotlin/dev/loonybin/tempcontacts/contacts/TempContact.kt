package dev.loonybin.tempcontacts.contacts

/**
 * A temp contact as this app cares about it. [expiryEpochMillis] is the value stored in the
 * RawContact's `sync1` column (epoch millis); it is read/written as an opaque [Long] here and
 * interpreted only by the expiry layer.
 */
data class TempContact(
    val rawContactId: Long,
    val displayName: String,
    val phoneNumber: String?,
    val expiryEpochMillis: Long?,
)
