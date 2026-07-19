package dev.loonybin.tempcontacts

import android.app.Application
import android.content.Context
import dev.loonybin.tempcontacts.account.AccountBootstrap
import dev.loonybin.tempcontacts.account.AccountWipe
import dev.loonybin.tempcontacts.account.SystemTempAccountManager
import dev.loonybin.tempcontacts.contacts.ContactsContractRepository
import dev.loonybin.tempcontacts.contacts.TempContactsRepository
import dev.loonybin.tempcontacts.expiry.ExpiryScheduler
import dev.loonybin.tempcontacts.expiry.WorkManagerExpiryScheduler

/**
 * Tiny hand-rolled service locator. Each package exposes an interface; the container wires the
 * concrete implementations together in one place so nothing else has to know the concrete types
 * (no DI framework needed for a scaffold this size).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val contactsRepository: TempContactsRepository by lazy { ContactsContractRepository(appContext) }

    private val accountManager by lazy { SystemTempAccountManager(appContext) }
    val accountBootstrap: AccountBootstrap get() = accountManager
    val accountWipe: AccountWipe get() = accountManager

    val expiryScheduler: ExpiryScheduler by lazy { WorkManagerExpiryScheduler(appContext) }
}

class TempContactsApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Register the periodic purge up front. The sweep is a no-op until the account exists
        // and contacts permission is granted, so it is safe to schedule at startup.
        container.expiryScheduler.schedulePeriodicSweep()
    }
}
