package io.github.plumblossomcat.torbox.data

import io.github.plumblossomcat.torbox.core.api.ControlOp
import io.github.plumblossomcat.torbox.core.api.TorBoxClient
import io.github.plumblossomcat.torbox.core.model.Transfer
import io.github.plumblossomcat.torbox.core.model.TransferKind
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class KindState(
    val items: List<Transfer> = emptyList(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0,
)

/** Single source of truth for the person's torrents, usenet and web downloads. */
class LibraryRepository(private val client: TorBoxClient, private val session: Session) {
    private val _state = MutableStateFlow(TransferKind.entries.associateWith { KindState() })
    val state: StateFlow<Map<TransferKind, KindState>> = _state.asStateFlow()

    private val locks = TransferKind.entries.associateWith { Mutex() }

    fun find(kind: TransferKind, id: Long): Transfer? = _state.value[kind]?.items?.firstOrNull { it.id == id }

    suspend fun refresh(kind: TransferKind, bypassCache: Boolean = true) {
        val lock = locks.getValue(kind)
        if (lock.isLocked) return
        lock.withLock {
            edit(kind) { it.copy(refreshing = true) }
            try {
                val items = client.list(kind, bypassCache).sortedByDescending { it.createdAt }
                edit(kind) { KindState(items = items, loaded = true, refreshing = false, error = null, updatedAt = System.currentTimeMillis()) }
            } catch (e: Exception) {
                session.inspect(e)
                edit(kind) { it.copy(refreshing = false, error = e.friendly(), loaded = true) }
            }
        }
    }

    suspend fun refreshAll(bypassCache: Boolean = true) = coroutineScope {
        TransferKind.entries.map { async { refresh(it, bypassCache) } }.forEach { it.await() }
    }

    /** Fetches one item fresh and merges it into the list. */
    suspend fun fetch(kind: TransferKind, id: Long): Transfer {
        val item = try {
            client.item(kind, id)
        } catch (e: Exception) {
            session.inspect(e)
            throw e
        }
        edit(kind) { s ->
            val exists = s.items.any { it.id == id }
            s.copy(items = if (exists) s.items.map { if (it.id == id) item else it } else listOf(item) + s.items)
        }
        return item
    }

    suspend fun control(kind: TransferKind, id: Long, op: ControlOp) {
        guarded { client.control(kind, id, op) }
        runCatching { fetch(kind, id) }
    }

    /** Deletes optimistically and restores the items if TorBox refuses. */
    suspend fun delete(kind: TransferKind, ids: Collection<Long>) {
        val before = _state.value.getValue(kind).items
        edit(kind) { s -> s.copy(items = s.items.filterNot { it.id in ids }) }
        try {
            ids.forEach { id -> guarded { client.control(kind, id, deleteOp(kind)) } }
        } catch (e: Exception) {
            edit(kind) { it.copy(items = before) }
            refresh(kind)
            throw e
        }
    }

    fun clear() {
        _state.value = TransferKind.entries.associateWith { KindState() }
    }

    private fun deleteOp(kind: TransferKind): ControlOp = when (kind) {
        TransferKind.TORRENT -> ControlOp.Torrent.DELETE
        TransferKind.USENET -> ControlOp.Usenet.DELETE
        TransferKind.WEB -> ControlOp.Web.DELETE
    }

    private suspend fun <T> guarded(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        session.inspect(e)
        throw e
    }

    private fun edit(kind: TransferKind, transform: (KindState) -> KindState) {
        _state.update { map -> map + (kind to transform(map.getValue(kind))) }
    }
}
