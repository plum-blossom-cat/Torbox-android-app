package io.github.plumblossomcat.torbox.ui.nav

import kotlinx.serialization.Serializable

/* Type-safe navigation destinations. Kinds are passed by enum name to keep routes primitive. */

@Serializable data object HomeRoute
@Serializable data object LibraryRoute
@Serializable data object SearchRoute
@Serializable data object AccountRoute

@Serializable data class DetailRoute(val kind: String, val id: Long)
@Serializable data class AddRoute(val kind: String? = null)
@Serializable data object QueuedRoute
@Serializable data object RssRoute
@Serializable data class RssEditRoute(val id: Long = -1)
@Serializable data class RssItemsRoute(val id: Long, val name: String)
@Serializable data object CloudRoute
@Serializable data object NotificationsRoute
@Serializable data object HostersRoute
@Serializable data object StatusRoute
@Serializable data object ChangelogRoute
@Serializable data object SettingsRoute
@Serializable data object AboutRoute
