package io.github.plumblossomcat.torbox.core

import com.google.common.truth.Truth.assertThat
import io.github.plumblossomcat.torbox.core.api.ControlOp
import io.github.plumblossomcat.torbox.core.api.SeedPreference
import io.github.plumblossomcat.torbox.core.api.TorBoxClient
import io.github.plumblossomcat.torbox.core.api.TorBoxException
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.model.TransferState
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TorBoxClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: TorBoxClient
    private var token: String? = "test-key"

    private fun fixture(name: String) =
        javaClass.getResource("/fixtures/$name")!!.readText()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = TorBoxClient(
            http = OkHttpClient(),
            token = { token },
            apiBase = server.url("/v1/api/"),
            searchBase = server.url("/search/"),
            rootBase = server.url("/"),
        )
    }

    @After
    fun tearDown() = server.close()

    private fun enqueue(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json").build())

    @Test
    fun parsesTorrentListLeniently() = runTest {
        enqueue(fixture("torrents.json"))
        val list = client.list(TransferKind.TORRENT, bypassCache = true)

        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/v1/api/torrents/mylist")
        assertThat(request.url.queryParameter("bypass_cache")).isEqualTo("true")
        assertThat(request.headers["Authorization"]).isEqualTo("Bearer test-key")

        assertThat(list).hasSize(3)
        val bunny = list[0]
        assertThat(bunny.state).isEqualTo(TransferState.READY)
        assertThat(bunny.files.map { it.name }).containsExactly("Big Buck Bunny.en.srt", "Big Buck Bunny.mp4").inOrder()
        assertThat(bunny.files[0].size).isEqualTo(140)
        assertThat(bunny.createdAt).isNotNull()
        assertThat(bunny.updatedAt).isNotNull()

        val sintel = list[1]
        assertThat(sintel.id).isEqualTo(4022)
        assertThat(sintel.state).isEqualTo(TransferState.STALLED)
        assertThat(sintel.peers).isEqualTo(3)
        assertThat(sintel.files).isEmpty()

        assertThat(list[2].state).isEqualTo(TransferState.SEEDING)
    }

    @Test
    fun mapsApiErrorsToFriendlyMessages() = runTest {
        enqueue(fixture("error_auth.json"), code = 403)
        try {
            client.me()
            fail("expected exception")
        } catch (e: TorBoxException.Api) {
            assertThat(e.code).isEqualTo("BAD_TOKEN")
            assertThat(e.isAuthError).isTrue()
            assertThat(e.message).contains("API key")
        }
    }

    @Test
    fun successFalseWith200IsStillAnError() = runTest {
        enqueue("""{"success":false,"error":"ACTIVE_LIMIT","detail":"x","data":null}""")
        try {
            client.addWeb("https://example.com/file.zip", null, null, false)
            fail("expected exception")
        } catch (e: TorBoxException.Api) {
            assertThat(e.code).isEqualTo("ACTIVE_LIMIT")
        }
    }

    @Test
    fun missingTokenFailsWithoutNetwork() = runTest {
        token = null
        try {
            client.list(TransferKind.USENET)
            fail("expected exception")
        } catch (e: TorBoxException.Api) {
            assertThat(e.isAuthError).isTrue()
        }
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun controlSendsKindSpecificBody() = runTest {
        enqueue("""{"success":true,"data":null}""")
        client.control(TransferKind.WEB, 7, ControlOp.Web.DELETE)
        val request = server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/v1/api/webdl/controlwebdownload")
        assertThat(request.body!!.utf8()).isEqualTo("""{"webdl_id":7,"operation":"delete"}""")

        enqueue("""{"success":true,"data":null}""")
        client.control(TransferKind.TORRENT, null, ControlOp.Torrent.DELETE)
        assertThat(server.takeRequest().body!!.utf8()).isEqualTo("""{"all":true,"operation":"delete"}""")
    }

    @Test
    fun createTorrentUsesMultipart() = runTest {
        enqueue("""{"success":true,"data":{"torrent_id":99,"hash":"abc","auth_id":"x"}}""")
        val created = client.addTorrent("magnet:?xt=urn:btih:abc", null, "Name", SeedPreference.NEVER, allowZip = true, asQueued = false)
        assertThat(created.torrentId).isEqualTo(99)
        val body = server.takeRequest().body!!.utf8()
        assertThat(body).contains("name=\"magnet\"")
        assertThat(body).contains("name=\"seed\"\r\n\r\n3\r\n")
    }

    @Test
    fun requestLinkReturnsUrl() = runTest {
        enqueue("""{"success":true,"data":"https://cdn.example.com/file.mp4?sig=1"}""")
        val url = client.requestLink(TransferKind.USENET, 5, 2)
        assertThat(url).startsWith("https://cdn.example.com")
        val req = server.takeRequest()
        assertThat(req.url.queryParameter("usenet_id")).isEqualTo("5")
        assertThat(req.url.queryParameter("file_id")).isEqualTo("2")
        assertThat(req.url.queryParameter("token")).isEqualTo("test-key")
    }

    @Test
    fun zipLinkWhenNoFile() {
        val link = client.permalink(TransferKind.TORRENT, 5, null)!!
        assertThat(link).contains("zip_link=true")
        assertThat(link).contains("redirect=true")
        assertThat(link).contains("torrent_id=5")
    }

    @Test
    fun parsesSearchResults() = runTest {
        enqueue(fixture("search.json"))
        val result = client.searchTorrents("sintel 2010")
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/search/torrents/search/sintel%202010")
        assertThat(result.metadata?.title).isEqualTo("Sintel")
        assertThat(result.metadata?.rating).isEqualTo(7.4)
        assertThat(result.torrents).hasSize(2)
        assertThat(result.torrents[0].cached).isTrue()
        assertThat(result.torrents[0].lastKnownSeeders).isEqualTo(120)
        assertThat(result.torrents[1].size).isEqualTo(700_000_000)
        assertThat(result.torrents[1].owned).isTrue()
    }

    @Test
    fun checkCachedHandlesObjectAndNull() = runTest {
        enqueue("""{"success":true,"data":{"DD8255ECDC7CA55FB0BBF81323D87062DB1F6D1C":{"name":"Bunny","size":10,"hash":"dd82"}}}""")
        val cached = client.checkCached(TransferKind.TORRENT, listOf("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c"))
        assertThat(cached.keys).containsExactly("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c")

        enqueue("""{"success":true,"data":null}""")
        assertThat(client.checkCached(TransferKind.TORRENT, listOf("x"))).isEmpty()
    }

    @Test
    fun singleItemAcceptsObjectOrList() = runTest {
        enqueue("""{"success":true,"data":{"id":3,"name":"One","download_state":"downloading","progress":0.5}}""")
        assertThat(client.item(TransferKind.USENET, 3).state).isEqualTo(TransferState.DOWNLOADING)
        enqueue("""{"success":true,"data":[{"id":3,"name":"One","download_state":"completed","download_present":true}]}""")
        assertThat(client.item(TransferKind.WEB, 3).state).isEqualTo(TransferState.READY)
    }

    @Test
    fun malformedBodyIsReported() = runTest {
        enqueue("<html>gateway</html>", code = 502)
        try {
            client.list(TransferKind.TORRENT)
            fail("expected exception")
        } catch (e: TorBoxException.Api) {
            assertThat(e.httpStatus).isEqualTo(502)
            assertThat(e.message).contains("502")
        }
    }
}
