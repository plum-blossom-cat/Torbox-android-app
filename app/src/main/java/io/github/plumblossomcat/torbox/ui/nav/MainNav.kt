package io.github.plumblossomcat.torbox.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.TbSnackbar
import io.github.plumblossomcat.torbox.ui.components.Toaster
import io.github.plumblossomcat.torbox.ui.components.pressable
import io.github.plumblossomcat.torbox.ui.screens.about.AboutScreen
import io.github.plumblossomcat.torbox.ui.screens.account.AccountNav
import io.github.plumblossomcat.torbox.ui.screens.account.AccountScreen
import io.github.plumblossomcat.torbox.ui.screens.add.AddScreen
import io.github.plumblossomcat.torbox.ui.screens.changelog.ChangelogScreen
import io.github.plumblossomcat.torbox.ui.screens.cloud.CloudScreen
import io.github.plumblossomcat.torbox.ui.screens.detail.DetailScreen
import io.github.plumblossomcat.torbox.ui.screens.home.HomeScreen
import io.github.plumblossomcat.torbox.ui.screens.hosters.HostersScreen
import io.github.plumblossomcat.torbox.ui.screens.library.LibraryScreen
import io.github.plumblossomcat.torbox.ui.screens.notifications.NotificationsScreen
import io.github.plumblossomcat.torbox.ui.screens.queued.QueuedScreen
import io.github.plumblossomcat.torbox.ui.screens.rss.RssEditScreen
import io.github.plumblossomcat.torbox.ui.screens.rss.RssItemsScreen
import io.github.plumblossomcat.torbox.ui.screens.rss.RssScreen
import io.github.plumblossomcat.torbox.ui.screens.search.SearchScreen
import io.github.plumblossomcat.torbox.ui.screens.settings.SettingsScreen
import io.github.plumblossomcat.torbox.ui.screens.status.StatusScreen
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlin.reflect.KClass

private data class Tab(val route: Any, val type: KClass<*>, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(HomeRoute, HomeRoute::class, "Home", TbIcons.Home),
    Tab(LibraryRoute, LibraryRoute::class, "Library", TbIcons.Library),
    Tab(SearchRoute, SearchRoute::class, "Search", TbIcons.Search),
    Tab(AccountRoute, AccountRoute::class, "Account", TbIcons.Account),
)

private fun NavDestination?.isTab(): Boolean = this != null && TABS.any { tab -> hierarchy.any { it.hasRoute(tab.type) } }

private fun NavHostController.openTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openDetail(kind: TransferKind, id: Long) = navigate(DetailRoute(kind.name, id)) { launchSingleTop = true }

@Composable
fun MainNav() {
    val container = LocalContainer.current
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toaster = remember { Toaster(snackbar, scope) }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val dark = Tb.colors.isDark

    // Hand-offs from other apps (shared links, opened .torrent files) and from notifications.
    LaunchedEffect(Unit) {
        container.pendingAdd.collect { pending ->
            if (pending != null && nav.currentDestination?.hasRoute(AddRoute::class) != true) {
                nav.navigate(AddRoute(pending.kind.name)) { launchSingleTop = true }
            }
        }
    }
    LaunchedEffect(Unit) {
        container.pendingOpen.collect { open ->
            if (open != null) {
                nav.openDetail(open.kind, open.id)
                container.pendingOpen.value = null
            }
        }
    }

    CompositionLocalProvider(LocalToaster provides toaster) {
        Scaffold(
            containerColor = Tb.colors.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) { TbSnackbar(it) } },
            bottomBar = {
                AnimatedVisibility(
                    visible = destination.isTab() || destination == null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    BottomBar(destination) { nav.openTab(it) }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = HomeRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
                enterTransition = { if (targetState.destination.isTab() && initialState.destination.isTab()) fadeIn(tween(180)) else slideInHorizontally(tween(260)) { it / 10 } + fadeIn(tween(260)) },
                exitTransition = { if (targetState.destination.isTab() && initialState.destination.isTab()) fadeOut(tween(120)) else fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(tween(220)) { it / 10 } + fadeOut(tween(200)) },
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpen = { nav.openDetail(it.kind, it.id) },
                        onAdd = { kind -> nav.navigate(AddRoute(kind?.name)) },
                        onSearch = { nav.openTab(SearchRoute) },
                        onLibrary = { nav.openTab(LibraryRoute) },
                        onNotifications = { nav.navigate(NotificationsRoute) },
                    )
                }
                composable<LibraryRoute> {
                    LibraryScreen(
                        onOpen = { nav.openDetail(it.kind, it.id) },
                        onAdd = { kind -> nav.navigate(AddRoute(kind.name)) },
                        onQueued = { nav.navigate(QueuedRoute) },
                    )
                }
                composable<SearchRoute> {
                    SearchScreen(onOpen = { kind, id -> nav.openDetail(kind, id) })
                }
                composable<AccountRoute> {
                    AccountScreen(
                        AccountNav(
                            onNotifications = { nav.navigate(NotificationsRoute) },
                            onQueued = { nav.navigate(QueuedRoute) },
                            onRss = { nav.navigate(RssRoute) },
                            onCloud = { nav.navigate(CloudRoute) },
                            onHosters = { nav.navigate(HostersRoute) },
                            onStatus = { nav.navigate(StatusRoute) },
                            onChangelog = { nav.navigate(ChangelogRoute) },
                            onSettings = { nav.navigate(SettingsRoute) },
                            onAbout = { nav.navigate(AboutRoute) },
                        ),
                    )
                }
                composable<DetailRoute> { back ->
                    val route = back.toRoute<DetailRoute>()
                    DetailScreen(
                        kind = TransferKind.valueOf(route.kind),
                        id = route.id,
                        onBack = { nav.popBackStack() },
                        onCloud = { nav.navigate(CloudRoute) },
                    )
                }
                composable<AddRoute> { back ->
                    val route = back.toRoute<AddRoute>()
                    AddScreen(
                        initialKind = route.kind?.let { runCatching { TransferKind.valueOf(it) }.getOrNull() },
                        onBack = { nav.popBackStack() },
                        onOpened = { kind, id ->
                            nav.popBackStack()
                            nav.openDetail(kind, id)
                        },
                        onLibrary = {
                            nav.popBackStack()
                            nav.openTab(LibraryRoute)
                        },
                        onQueued = { nav.navigate(QueuedRoute) },
                        onHosters = { nav.navigate(HostersRoute) },
                    )
                }
                composable<QueuedRoute> { QueuedScreen(onBack = { nav.popBackStack() }) }
                composable<NotificationsRoute> { NotificationsScreen(onBack = { nav.popBackStack() }) }
                composable<RssRoute> {
                    RssScreen(
                        onBack = { nav.popBackStack() },
                        onEdit = { id -> nav.navigate(RssEditRoute(id)) },
                        onItems = { feed -> nav.navigate(RssItemsRoute(feed.id, feed.name)) },
                        onPlans = { container.device.browse("https://torbox.app/pricing", dark) },
                    )
                }
                composable<RssEditRoute> { back -> RssEditScreen(back.toRoute<RssEditRoute>().id, onBack = { nav.popBackStack() }) }
                composable<RssItemsRoute> { back ->
                    val route = back.toRoute<RssItemsRoute>()
                    RssItemsScreen(route.id, route.name, onBack = { nav.popBackStack() })
                }
                composable<CloudRoute> { CloudScreen(onBack = { nav.popBackStack() }) }
                composable<HostersRoute> { HostersScreen(onBack = { nav.popBackStack() }) }
                composable<StatusRoute> { StatusScreen(onBack = { nav.popBackStack() }, onChangelog = { nav.navigate(ChangelogRoute) }) }
                composable<ChangelogRoute> { ChangelogScreen(onBack = { nav.popBackStack() }) }
                composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}

@Composable
internal fun BottomBar(destination: NavDestination?, onSelect: (Any) -> Unit) {
    val c = Tb.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.background),
    ) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { tab ->
                val selected = destination?.hierarchy?.any { it.hasRoute(tab.type) } == true ||
                    (destination == null && tab.route == HomeRoute)
                BarItem(tab, selected, Modifier.weight(1f)) { onSelect(tab.route) }
            }
        }
    }
}

@Composable
private fun BarItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Tb.colors
    val interaction = remember { MutableInteractionSource() }
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .pressable(interaction, role = Role.Tab, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(tab.icon, contentDescription = null, tint = if (selected) c.ink else c.inkFaint, modifier = Modifier.size(21.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(tab.label, style = Tb.type.label.copy(fontSize = Tb.type.eyebrow.fontSize), color = if (selected) c.ink else c.inkFaint)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .size(width = if (selected) 16.dp else 0.dp, height = 2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (selected) c.ink else c.background),
            )
        }
    }
}
