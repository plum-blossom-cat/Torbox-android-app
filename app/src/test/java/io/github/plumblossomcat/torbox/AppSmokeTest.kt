package io.github.plumblossomcat.torbox

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasScrollAction
import com.google.common.truth.Truth.assertThat
import io.github.plumblossomcat.torbox.data.SecretBox
import io.github.plumblossomcat.torbox.ui.components.Motion
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/** Keystore isn't available under Robolectric; the smoke test stores secrets as-is. */
class PlainSecrets : SecretBox() {
    override fun seal(plain: String) = plain
    override fun open(sealed: String): String? = sealed
}

/** Serves canned TorBox responses and records every request the app makes. */
object FakeTorBox {
    val requests = CopyOnWriteArrayList<String>()
    const val KEY = "3f6c1d2e-8b7a-4c59-9e21-5d0f4a7b8c90"

    private fun ok(data: String) = MockResponse.Builder()
        .code(200)
        .addHeader("Content-Type", "application/json")
        .body("""{"success":true,"error":null,"detail":"ok","data":$data}""")
        .build()

    val server: MockWebServer by lazy {
        MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.url.encodedPath
                    requests += "${request.method} $path"
                    val auth = request.headers["Authorization"]
                    val needsAuth = path.startsWith("/v1/api/") && !path.endsWith("/requestdl") && !path.endsWith("/stats") && !path.contains("changelogs")
                    if (needsAuth && auth != "Bearer $KEY") {
                        return MockResponse.Builder().code(403).body("""{"success":false,"error":"BAD_TOKEN","detail":"Invalid token","data":null}""").build()
                    }
                    return when {
                        path == "/v1/api/user/me" -> ok("""{"id":48213,"email":"maya.okafor@example.com","plan":2,"total_downloaded":3672000000000,"is_subscribed":true,"premium_expires_at":"2027-05-01T00:00:00Z","created_at":"2024-02-11T09:12:00Z","user_referral":"8f14e45f-ceea-467a-9c4e-a6b1c3d5e2f0"}""")
                        path == "/v1/api/torrents/mylist" && request.url.queryParameter("id") != null -> ok(TORRENT)
                        path == "/v1/api/torrents/mylist" -> ok("[$TORRENT]")
                        path.endsWith("/mylist") -> ok("[]")
                        path.endsWith("/checkcached") -> ok("{}")
                        path.endsWith("/requestdl") -> ok("\"https://cdn.example.com/file.mkv\"")
                        path.endsWith("/notifications/mynotifications") -> ok("""[{"id":3,"title":"Download ready","message":"Big Buck Bunny finished.","created_at":"2026-10-23T10:00:00Z"}]""")
                        path.contains("/queued/getqueued") -> ok("[]")
                        path.endsWith("/integration/jobs") -> ok("[]")
                        else -> ok("null")
                    }
                }
            }
            start()
        }
    }

    private const val TORRENT = """{"id":4020,"hash":"dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c","name":"Big Buck Bunny (2008) 1080p","size":1154000000,"active":false,"download_state":"cached","progress":1,"download_present":true,"download_finished":true,"created_at":"2026-10-23T08:00:00Z","files":[{"id":1,"name":"Big Buck Bunny/Big Buck Bunny.mkv","short_name":"Big Buck Bunny.mkv","size":1100000000,"mimetype":"video/x-matroska"},{"id":2,"name":"Big Buck Bunny/Subs/English.srt","short_name":"English.srt","size":42000}]}"""
}

class SmokeTestApp : TorBoxApp() {
    override fun createContainer(): AppContainer {
        Motion.reduced = true
        val base = FakeTorBox.server.url("/").toString()
        return AppContainer(this, PlainSecrets(), apiBase = base + "v1/api/", searchBase = base + "search/", rootBase = base)
    }
}

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = PHONE, application = SmokeTestApp::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeout: Long = 10_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeout)

    @Test
    fun signInBrowseAndOpenADownload() {
        // Sign in through the real form.
        waitForText("Your library")
        compose.onNode(hasSetTextAction()).performTextInput(FakeTorBox.KEY)
        compose.onNodeWithText("Continue").performClick()

        // Home loads the account and library.
        waitForText("Big Buck Bunny (2008) 1080p")
        waitForText("Pro")

        // Library tab lists the torrent; open it.
        compose.onAllNodesWithText("Library").onFirst().performClick()
        waitForText("Torrents")
        compose.onAllNodesWithText("Big Buck Bunny (2008) 1080p").onFirst().performClick()

        // Detail shows the file tree, with the shared root folder dropped.
        waitForText("Big Buck Bunny.mkv")
        compose.onNodeWithText("Subs").assertExists()
        compose.onNodeWithText("Hash".uppercase()).assertExists()

        // Back, then Account shows the profile.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("Torrents")
        compose.onAllNodesWithText("Account").onFirst().performClick()
        waitForText("maya.okafor@example.com")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Settings"))
        compose.onNodeWithText("Settings").performClick()
        waitForText("When you tap a file".uppercase())

        assertThat(FakeTorBox.requests).contains("GET /v1/api/user/me")
        assertThat(FakeTorBox.requests).contains("GET /v1/api/torrents/mylist")
        assertThat(FakeTorBox.requests).contains("GET /v1/api/usenet/mylist")
        assertThat(FakeTorBox.requests).contains("GET /v1/api/webdl/mylist")
    }
}
