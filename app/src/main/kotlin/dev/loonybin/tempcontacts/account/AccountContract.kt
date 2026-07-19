package dev.loonybin.tempcontacts.account

import android.accounts.Account

/**
 * The single source of truth for the dedicated account's identity.
 *
 * [ACCOUNT_TYPE] MUST match `res/xml/authenticator.xml` (accountType) and
 * `res/xml/sync_adapter.xml` (accountType), or the framework silently refuses to bind
 * the authenticator / sync adapter.
 */
object AccountContract {
    /** Custom, app-owned account type. Nothing else on the device uses it. */
    const val ACCOUNT_TYPE: String = "dev.loonybin.tempcontacts"

    /** Display name for the one account instance we ever create. */
    const val ACCOUNT_NAME: String = "Temporary"

    /** System contacts authority the (no-op) sync adapter is bound to. */
    const val CONTACTS_AUTHORITY: String = "com.android.contacts"

    /** The one and only account this app manages. */
    val account: Account get() = Account(ACCOUNT_NAME, ACCOUNT_TYPE)
}

/**
 * Cross-package boundary: creates the dedicated account if it is not already present.
 * UI and expiry code depend on this interface, never on [SystemTempAccountManager].
 */
interface AccountBootstrap {
    /** Ensures the temp account exists and returns it. Idempotent. */
    fun ensureAccount(): Account
}

/**
 * Cross-package boundary: the "nuclear" bulk delete. Removing the account atomically drops
 * every RawContact owned by it — no per-row iteration, no soft-delete residue.
 */
interface AccountWipe {
    /**
     * Removes the temp account (and, as a side effect, all of its contacts) if present.
     * @return true if an account was removed, false if there was nothing to remove.
     */
    suspend fun removeAccount(): Boolean
}
