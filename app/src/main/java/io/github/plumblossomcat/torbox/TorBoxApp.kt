package io.github.plumblossomcat.torbox

import android.app.Application
import android.content.Context
import io.github.plumblossomcat.torbox.core.api.TorBoxClient
import io.github.plumblossomcat.torbox.data.AccountRepository
import io.github.plumblossomcat.torbox.data.CompletionWorker
import io.github.plumblossomcat.torbox.data.DeviceActions
import io.github.plumblossomcat.torbox.data.LibraryRepository
import io.github.plumblossomcat.torbox.data.PendingAdd
import io.github.plumblossomcat.torbox.data.PendingOpen
import io.github.plumblossomcat.torbox.data.SecretBox
import io.github.plumblossomcat.torbox.data.Session
import io.github.plumblossomcat.torbox.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import io.github.plumblossomcat.torbox.ui.components.Motion
import java.util.concurrent.TimeUnit

open class TorBoxApp : Application() {
    lateinit var container: AppContainer
        private set

    /** Overridden in tests to point the app at a fake server. */
    protected open fun createContainer(): AppContainer = AppContainer(this)

    override fun onCreate() {
        super.onCreate()
        Motion.reduced = android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        container = createContainer()
        CompletionWorker.ensureChannel(this)
        container.scope.launch {
            container.settings.settings.map { it.notifyOnComplete }.distinctUntilChanged().collect { enabled ->
                runCatching { CompletionWorker.schedule(this@TorBoxApp, enabled) }
            }
        }
    }
}

/** Hand-wired dependencies. Small enough that a DI framework would only add ceremony. */
class AppContainer(
    context: Context,
    val secrets: SecretBox = SecretBox(),
    apiBase: String = TorBoxClient.DEFAULT_API_BASE,
    searchBase: String = TorBoxClient.DEFAULT_SEARCH_BASE,
    rootBase: String = TorBoxClient.DEFAULT_ROOT,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(context.applicationContext, secrets)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "TorBox-Android/${BuildConfig.VERSION_NAME}").build())
        }
        .build()
    val session = Session(settings, scope)
    val client = TorBoxClient(http, token = { session.key }, apiBase = apiBase.toHttpUrl(), searchBase = searchBase.toHttpUrl(), rootBase = rootBase.toHttpUrl())
    val library = LibraryRepository(client, session)
    val account = AccountRepository(client, session, settings)
    val device = DeviceActions(context.applicationContext)

    /** Hand-offs from intents to the UI. Consumed (set back to null) once handled. */
    val pendingAdd = MutableStateFlow<PendingAdd?>(null)
    val pendingOpen = MutableStateFlow<PendingOpen?>(null)
}
