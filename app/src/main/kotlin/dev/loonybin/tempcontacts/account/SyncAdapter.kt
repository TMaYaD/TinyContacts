package dev.loonybin.tempcontacts.account

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle

/**
 * Intentionally empty sync adapter.
 *
 * The framework requires a SyncAdapter to be registered for our account type against the
 * contacts authority for the account to be considered a legitimate, backed contacts source.
 * We want the *registration*, not the *behavior*: [onPerformSync] does nothing, so temp
 * contacts never leave the device. `supportsUploading=false` in sync_adapter.xml reinforces
 * that there is nothing to upload.
 */
class SyncAdapter(
    context: Context,
    autoInitialize: Boolean,
) : AbstractThreadedSyncAdapter(context, autoInitialize) {

    override fun onPerformSync(
        account: Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProviderClient?,
        syncResult: SyncResult?,
    ) {
        // No-op by design. Local-only account: there is no remote to sync with.
    }
}
