package io.github.plumblossomcat.torbox.data

import io.github.plumblossomcat.torbox.core.api.TorBoxClient
import io.github.plumblossomcat.torbox.core.api.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountRepository(private val client: TorBoxClient, private val session: Session) {
    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _unread = MutableStateFlow(0)
    /** Count of notifications on the account; there is no read state in the API, so this is "not cleared". */
    val unread: StateFlow<Int> = _unread.asStateFlow()

    suspend fun refresh() {
        try {
            _user.value = client.me()
            _error.value = null
        } catch (e: Exception) {
            session.inspect(e)
            _error.value = e.friendly()
        }
    }

    suspend fun refreshUnread() {
        runCatching { client.notifications().size }.onSuccess { _unread.value = it }
    }

    fun setUnread(count: Int) {
        _unread.value = count
    }

    fun set(user: UserDto) {
        _user.value = user
    }

    fun clear() {
        _user.value = null
        _error.value = null
        _unread.value = 0
    }
}
