package io.github.plumblossomcat.torbox

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.plumblossomcat.torbox.core.api.PostProcessing
import io.github.plumblossomcat.torbox.core.api.UploadFile
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.data.AppSettings
import io.github.plumblossomcat.torbox.data.KindState
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.ui.nav.BottomBar
import io.github.plumblossomcat.torbox.ui.screens.account.AccountContent
import io.github.plumblossomcat.torbox.ui.screens.account.AccountNav
import io.github.plumblossomcat.torbox.ui.screens.add.AddContent
import io.github.plumblossomcat.torbox.ui.screens.add.AddForm
import io.github.plumblossomcat.torbox.ui.screens.add.CacheStatus
import io.github.plumblossomcat.torbox.ui.screens.changelog.ChangelogContent
import io.github.plumblossomcat.torbox.ui.screens.cloud.CloudContent
import io.github.plumblossomcat.torbox.ui.screens.detail.DetailContent
import io.github.plumblossomcat.torbox.ui.screens.detail.DetailState
import io.github.plumblossomcat.torbox.ui.screens.detail.DetailUi
import io.github.plumblossomcat.torbox.ui.screens.detail.FileTree
import io.github.plumblossomcat.torbox.ui.screens.home.HomeContent
import io.github.plumblossomcat.torbox.ui.screens.home.HomeState
import io.github.plumblossomcat.torbox.ui.screens.hosters.HostersContent
import io.github.plumblossomcat.torbox.ui.screens.library.LibraryActions
import io.github.plumblossomcat.torbox.ui.screens.library.LibraryContent
import io.github.plumblossomcat.torbox.ui.screens.library.LibraryState
import io.github.plumblossomcat.torbox.ui.screens.library.LibraryUi
import io.github.plumblossomcat.torbox.ui.screens.notifications.NotificationsContent
import io.github.plumblossomcat.torbox.ui.screens.queued.QueuedContent
import io.github.plumblossomcat.torbox.ui.screens.rss.RssContent
import io.github.plumblossomcat.torbox.ui.screens.rss.RssEditContent
import io.github.plumblossomcat.torbox.ui.screens.rss.RssForm
import io.github.plumblossomcat.torbox.ui.screens.search.SearchContent
import io.github.plumblossomcat.torbox.ui.screens.search.SearchUi
import io.github.plumblossomcat.torbox.ui.screens.settings.SettingsActions
import io.github.plumblossomcat.torbox.ui.screens.settings.SettingsContent
import io.github.plumblossomcat.torbox.ui.screens.status.ServiceStatus
import io.github.plumblossomcat.torbox.ui.screens.status.StatusContent
import io.github.plumblossomcat.torbox.ui.components.Stat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDateTime

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = PHONE, application = Application::class)
class ScreensScreenshotTest(private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()

    private val evening = LocalDateTime.of(2026, 10, 23, 19, 30)
    private val all = Samples.torrents + Samples.usenet + Samples.web

    @Test
    fun home() = compose.shoot("02_home", dark) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                HomeContent(
                    HomeState(
                        user = Samples.user,
                        active = all.filter { it.state.isActive },
                        recent = all.sortedByDescending { it.createdAt }.take(6),
                        total = all.size,
                        loaded = true,
                        unread = 2,
                    ),
                    {}, {}, {}, {}, {}, {}, now = evening,
                )
            }
            BottomBar(null) {}
        }
    }

    @Test
    fun homeEmpty() = compose.shoot("02_home_empty", dark) {
        HomeContent(HomeState(user = Samples.user.copy(plan = 1, isSubscribed = false), loaded = true), {}, {}, {}, {}, {}, {}, now = evening.withHour(9))
    }

    @Test
    fun library() = compose.shoot("03_library", dark) {
        LibraryContent(
            LibraryState(
                ui = LibraryUi(),
                items = Samples.torrents,
                kindState = KindState(items = Samples.torrents, loaded = true),
                counts = mapOf(TransferKind.TORRENT to Samples.torrents.size, TransferKind.USENET to 2, TransferKind.WEB to 1),
            ),
            LibraryActions(),
        )
    }

    @Test
    fun librarySelecting() = compose.shoot("03_library_select", dark) {
        LibraryContent(
            LibraryState(
                ui = LibraryUi(selection = setOf(4016L, 4018L)),
                items = Samples.torrents,
                kindState = KindState(items = Samples.torrents, loaded = true),
                counts = mapOf(TransferKind.TORRENT to Samples.torrents.size, TransferKind.USENET to 2, TransferKind.WEB to 1),
            ),
            LibraryActions(),
        )
    }

    @Test
    fun libraryUsenetEmpty() = compose.shoot("03_library_empty", dark) {
        LibraryContent(
            LibraryState(
                ui = LibraryUi(kind = TransferKind.WEB),
                items = emptyList(),
                kindState = KindState(loaded = true),
                counts = mapOf(TransferKind.TORRENT to 6, TransferKind.USENET to 2, TransferKind.WEB to 0),
            ),
            LibraryActions(),
        )
    }

    @Test
    fun detailReady() = compose.shoot("04_detail", dark) {
        val item = Samples.torrents[1]
        DetailContent(
            DetailState(item, DetailUi(loading = false), FileTree.rows(item.files, emptySet()), AppSettings()),
            TransferKind.TORRENT, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, { _, _ -> }, {},
        )
    }

    @Test
    fun detailDownloading() = compose.shoot("04_detail_live", dark) {
        val item = Samples.torrents[0]
        DetailContent(
            DetailState(item, DetailUi(loading = false), emptyList(), AppSettings()),
            TransferKind.TORRENT, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}, { _, _ -> }, {}, {}, { _, _ -> }, {},
        )
    }

    @Test
    fun addTorrent() = compose.shoot("05_add", dark) {
        AddContent(
            AddForm(
                text = "magnet:?xt=urn:btih:08ada5a7a6183aae1e09d831df6748d566095a10&dn=Sintel",
                cache = CacheStatus.Cached(1, 1, "Sintel", 1_290L * 1024 * 1024),
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun addUsenet() = compose.shoot("05_add_usenet", dark) {
        AddContent(
            AddForm(kind = TransferKind.USENET, file = UploadFile("Cosmos.Laundromat.2015.nzb", ByteArray(48_210), "application/x-nzb"), postProcessing = PostProcessing.DEFAULT),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun search() = compose.shoot("06_search", dark) {
        SearchContent(
            SearchUi(query = "sintel", submitted = "sintel", result = Load.Ready(Samples.search), added = mapOf("a2" to 4018L)),
            emptyList(), {}, {}, {}, {}, {}, {}, {}, {},
        )
    }

    @Test
    fun searchRecent() = compose.shoot("06_search_recent", dark) {
        SearchContent(SearchUi(), listOf("sintel", "big buck bunny 1080p", "ubuntu 26.04"), {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun account() = compose.shoot("07_account", dark) {
        AccountContent(Samples.user, null, 2, false, AccountNav(), {}, { _, _ -> }, {}, { _, _ -> }, {}, {}, {})
    }

    @Test
    fun settings() = compose.shoot("08_settings", dark) {
        SettingsContent(AppSettings(notifyOnComplete = true), {}, SettingsActions())
    }

    @Test
    fun queued() = compose.shoot("09_queued", dark) {
        QueuedContent(Load.Ready(Samples.queued), false, {}, {}, {}, {}, {})
    }

    @Test
    fun notifications() = compose.shoot("10_notifications", dark) {
        NotificationsContent(Load.Ready(Samples.notifications), false, {}, {}, {}, {}, {})
    }

    @Test
    fun rss() = compose.shoot("11_rss", dark) {
        RssContent(Load.Ready(Samples.feeds), false, {}, {}, {}, {}, { _, _ -> }, {})
    }

    @Test
    fun rssEdit() = compose.shoot("11_rss_edit", dark) {
        RssEditContent(RssForm(name = "Blender Studio releases", url = "https://studio.blender.org/films/feed.xml", doRegex = "2160p|4K"), true, {}, {}, {})
    }

    @Test
    fun cloud() = compose.shoot("12_cloud", dark) {
        CloudContent(
            Load.Ready(Samples.jobs), false,
            mapOf(io.github.plumblossomcat.torbox.core.api.IntegrationProvider.GOOGLE_DRIVE to "x", io.github.plumblossomcat.torbox.core.api.IntegrationProvider.GOFILE to "y"),
            {}, {}, { _, _ -> }, {}, {}, {}, {},
        )
    }

    @Test
    fun hosters() = compose.shoot("13_hosters", dark) {
        HostersContent(Load.Ready(Samples.hosters), false, {}, {})
    }

    @Test
    fun status() = compose.shoot("14_status", dark) {
        StatusContent(
            Load.Ready(
                ServiceStatus(
                    up = true,
                    stats = listOf(
                        Stat("Total users", "1.2M"), Stat("Total downloads", "48.6M"),
                        Stat("Bytes downloaded", "92.4 PB"), Stat("Active torrents", "312K"),
                        Stat("Servers online", "64"), Stat("Usenet downloads", "8.1M"),
                    ),
                    checkedAt = Instant.now(),
                ),
            ),
            false, {}, {}, {},
        )
    }

    @Test
    fun changelog() = compose.shoot("15_changelog", dark) {
        ChangelogContent(Load.Ready(Samples.changelog), false, {}, {}, {})
    }

    @Test
    fun errorState() = compose.shoot("16_error", dark) {
        NotificationsContent(Load.Failed("Can't reach TorBox. Check your connection and try again."), false, {}, {}, {}, {}, {})
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0}")
        fun params() = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
