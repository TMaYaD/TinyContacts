package dev.loonybin.tempcontacts.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The concrete [AccountBootstrap] + [AccountWipe] backed by the system [AccountManager].
 * This is the only class in the app that talks to AccountManager. It knows nothing about UI,
 * contacts columns, or expiry rules.
 */
class SystemTempAccountManager(
    context: Context,
) : AccountBootstrap, AccountWipe {

    private val appContext = context.applicationContext
    private val accountManager = AccountManager.get(appContext)

    override fun ensureAccount(): Account {
        val account = AccountContract.account
        val existing = accountManager.getAccountsByType(AccountContract.ACCOUNT_TYPE)
        if (existing.none { it.name == account.name }) {
            // addAccountExplicitly succeeds for our own account type without any user
            // interaction or extra permission (the authenticator is registered in-app).
            accountManager.addAccountExplicitly(account, /* password = */ null, /* userdata = */ null)
        }
        // Mark the account syncable for the contacts authority and turn *off* automatic sync.
        // We keep it syncable (so the OS treats it as a real backed source) but never let the
        // no-op adapter run on a schedule.
        ContentResolver.setIsSyncable(account, AccountContract.CONTACTS_AUTHORITY, 1)
        ContentResolver.setSyncAutomatically(account, AccountContract.CONTACTS_AUTHORITY, false)
        return account
    }

    override suspend fun removeAccount(): Boolean = withContext(Dispatchers.IO) {
        val toRemove = accountManager
            .getAccountsByType(AccountContract.ACCOUNT_TYPE)
            .toList()
        if (toRemove.isEmpty()) return@withContext false

        // removeAccountExplicitly (API 22+) is synchronous and atomic: dropping the account
        // cascades to every RawContact owned by it. This is the "nuclear" bulk delete.
        var removedAny = false
        for (account in toRemove) {
            @Suppress("DEPRECATION")
            val removed = accountManager.removeAccountExplicitly(account)
            removedAny = removedAny || removed
        }
        removedAny
    }
}
