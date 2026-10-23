package io.github.plumblossomcat.torbox.data

import io.github.plumblossomcat.torbox.core.api.TorBoxClient
import io.github.plumblossomcat.torbox.core.api.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AccountRepository(private val client: TorBoxClient, private val session: Session, private val settings: SettingsStore) {
    private val _user = MutableStateFlow<UserDto?>(null)
    val user: StateFlow<UserDto?> = _user.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _unread = MutableStateFlow(0)
    /** Notifications newer than the last one seen in the app. The API has no read state of its own. */
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
        val seen = settings.lastSeenNotification()
        runCatching { client.notifications() }.onSuccess { list -> _unread.value = list.count { it.id > seen } }
    }

    suspend fun markSeen(latestId: Long) {
        if (latestId > settings.lastSeenNotification()) settings.setLastSeenNotification(latestId)
        _unread.value = 0
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
