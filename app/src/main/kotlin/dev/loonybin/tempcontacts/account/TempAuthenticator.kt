package dev.loonybin.tempcontacts.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.content.Context

/**
 * A deliberately inert authenticator. There is no server, no login, no token — the account
 * exists purely so the OS treats our RawContacts as belonging to a real, backed account
 * (and therefore does not garbage-collect them).
 *
 * Every method that would talk to a backend returns an empty/no-op result. We never surface
 * an "add account" UI: the account is created programmatically via [AccountBootstrap].
 */
class TempAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {

    override fun editProperties(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
    ) = null

    // No interactive add-account flow — creation is programmatic. Return an empty bundle.
    override fun addAccount(
        response: AccountAuthenticatorResponse?,
        accountType: String?,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: android.os.Bundle?,
    ) = android.os.Bundle()

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        options: android.os.Bundle?,
    ) = null

    // No auth tokens exist for a local-only account.
    override fun getAuthToken(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: android.os.Bundle?,
    ) = android.os.Bundle()

    override fun getAuthTokenLabel(authTokenType: String?): String? = null

    override fun updateCredentials(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        authTokenType: String?,
        options: android.os.Bundle?,
    ) = null

    override fun hasFeatures(
        response: AccountAuthenticatorResponse?,
        account: Account?,
        features: Array<out String>?,
    ) = android.os.Bundle().apply { putBoolean(android.accounts.AccountManager.KEY_BOOLEAN_RESULT, false) }
}
