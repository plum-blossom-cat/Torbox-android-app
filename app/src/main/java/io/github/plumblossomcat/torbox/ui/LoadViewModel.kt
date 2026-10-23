package io.github.plumblossomcat.torbox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.data.friendly
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads one value, supports pull-to-refresh, and reports a revoked key to the session. */
open class LoadViewModel<T>(
    protected val container: AppContainer,
    private val fetch: suspend AppContainer.() -> T,
) : ViewModel() {
    private val _load = MutableStateFlow<Load<T>>(Load.Loading)
    val load: StateFlow<Load<T>> = _load.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** One-shot snackbar text. */
    val message = MutableStateFlow<String?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload(userInitiated = _load.value is Load.Ready) }
    }

    suspend fun reload(userInitiated: Boolean = false) {
        if (userInitiated) _refreshing.value = true
        try {
            _load.value = Load.Ready(container.fetch())
        } catch (e: Exception) {
            container.session.inspect(e)
            if (_load.value !is Load.Ready) _load.value = Load.Failed(e.friendly()) else message.value = e.friendly()
        } finally {
            _refreshing.value = false
        }
    }

    /** Runs a mutation, then reloads. Errors become a snackbar message. */
    protected fun mutate(success: String?, block: suspend AppContainer.() -> Unit) {
        viewModelScope.launch {
            try {
                container.block()
                success?.let { message.value = it }
                reload()
            } catch (e: Exception) {
                container.session.inspect(e)
                message.value = e.friendly()
            }
        }
    }

    protected fun update(transform: (T) -> T) {
        val current = _load.value
        if (current is Load.Ready) _load.value = Load.Ready(transform(current.value))
    }
}
