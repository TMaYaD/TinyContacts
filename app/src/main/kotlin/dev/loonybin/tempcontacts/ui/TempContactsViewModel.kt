package dev.loonybin.tempcontacts.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.loonybin.tempcontacts.TempContactsApp
import dev.loonybin.tempcontacts.expiry.ExpiryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A temp contact as the UI shows it, with remaining lifetime already computed. */
data class TempContactUi(
    val rawContactId: Long,
    val displayName: String,
    val phoneNumber: String?,
    val remainingMillis: Long?,
    val expired: Boolean,
)

data class ScreenState(
    val loading: Boolean = false,
    val contacts: List<TempContactUi> = emptyList(),
    val message: String? = null,
)

/**
 * Bridges the Compose UI to the account + contacts + expiry boundaries. Only depends on the
 * interfaces exposed by those packages (via [TempContactsApp.container]).
 */
class TempContactsViewModel(app: Application) : AndroidViewModel(app) {

    private val container get() = getApplication<TempContactsApp>().container

    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val now = System.currentTimeMillis()
            val contacts = runCatching { container.contactsRepository.queryContacts() }
                .getOrDefault(emptyList())
                .map { c ->
                    val remaining = ExpiryPolicy.remainingMillis(c.expiryEpochMillis, now)
                    TempContactUi(
                        rawContactId = c.rawContactId,
                        displayName = c.displayName,
                        phoneNumber = c.phoneNumber,
                        remainingMillis = remaining,
                        expired = ExpiryPolicy.isExpired(c.expiryEpochMillis, now),
                    )
                }
            _state.value = ScreenState(loading = false, contacts = contacts)
        }
    }

    fun addContact(name: String, phone: String?, duration: DurationOption) {
        if (name.isBlank()) {
            _state.value = _state.value.copy(message = "Name can't be empty")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                container.accountBootstrap.ensureAccount()
                val expiry = System.currentTimeMillis() + duration.millis
                container.contactsRepository.insert(name.trim(), phone?.trim()?.ifBlank { null }, expiry)
            }
            _state.value = _state.value.copy(message = "Added \"$name\" for ${duration.label}")
            refresh()
        }
    }

    /** The "nuclear" wipe: remove the account (drops every temp contact atomically). */
    fun wipeAll() {
        viewModelScope.launch {
            val removed = container.accountWipe.removeAccount()
            _state.value = _state.value.copy(
                message = if (removed) "All temp contacts wiped" else "Nothing to wipe",
            )
            refresh()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
