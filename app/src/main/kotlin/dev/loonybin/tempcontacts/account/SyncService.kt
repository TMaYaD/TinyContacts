package dev.loonybin.tempcontacts.account

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Bound service exposing the (no-op) [SyncAdapter]. Declared in the manifest with the
 * `android.content.SyncAdapter` intent filter, the sync_adapter.xml meta-data, and the
 * CONTACTS_STRUCTURE meta-data that gives the account its "Temporary" display label.
 *
 * A single adapter instance is shared across bind calls, per the framework contract.
 */
class SyncService : Service() {

    override fun onCreate() {
        super.onCreate()
        synchronized(lock) {
            if (adapter == null) {
                adapter = SyncAdapter(applicationContext, autoInitialize = true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = adapter!!.syncAdapterBinder

    companion object {
        private val lock = Any()
        private var adapter: SyncAdapter? = null
    }
}
