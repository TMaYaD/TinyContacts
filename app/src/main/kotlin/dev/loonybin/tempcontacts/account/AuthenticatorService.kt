package dev.loonybin.tempcontacts.account

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Bound service that hands the framework our [TempAuthenticator]'s binder when it wants to
 * talk to accounts of our type. Declared in the manifest with the
 * `android.accounts.AccountAuthenticator` intent filter + meta-data.
 */
class AuthenticatorService : Service() {

    private val authenticator by lazy { TempAuthenticator(this) }

    override fun onBind(intent: Intent?): IBinder = authenticator.iBinder
}
