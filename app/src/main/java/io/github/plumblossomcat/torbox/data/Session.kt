package io.github.plumblossomcat.torbox.data

import io.github.plumblossomcat.torbox.core.api.TorBoxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    /** [reason] explains an involuntary sign-out, e.g. a revoked key. */
    data class SignedOut(val reason: String? = null) : SessionState
    data object SignedIn : SessionState
}

class Session(private val settings: SettingsStore, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Read synchronously by the HTTP client on every request. */
    @Volatile
    var key: String? = null
        private set

    init {
        scope.launch {
            key = settings.apiKey()
            _state.value = if (key == null) SessionState.SignedOut() else SessionState.SignedIn
        }
    }

    suspend fun signIn(apiKey: String) {
        settings.setApiKey(apiKey)
        key = apiKey
        _state.value = SessionState.SignedIn
    }

    suspend fun signOut(reason: String? = null) {
        key = null
        settings.clearAccount()
        _state.value = SessionState.SignedOut(reason)
    }

    /** Call with any failure; signs out if TorBox says the key is no longer valid. */
    fun inspect(error: Throwable) {
        if (error is TorBoxException.Api && error.isAuthError && key != null) {
            scope.launch { signOut("TorBox rejected your API key. It may have been reset — sign in with your current key.") }
        }
    }
}
