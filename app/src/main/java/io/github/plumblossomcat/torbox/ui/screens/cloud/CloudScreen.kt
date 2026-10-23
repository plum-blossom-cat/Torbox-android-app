package io.github.plumblossomcat.torbox.ui.screens.cloud

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.core.api.IntegrationJobDto
import io.github.plumblossomcat.torbox.core.api.IntegrationProvider
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.core.util.Timestamps
import io.github.plumblossomcat.torbox.data.Load
import io.github.plumblossomcat.torbox.ui.LoadViewModel
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.ButtonStyle
import io.github.plumblossomcat.torbox.ui.components.EmptyState
import io.github.plumblossomcat.torbox.ui.components.ErrorState
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.Meter
import io.github.plumblossomcat.torbox.ui.components.Panel
import io.github.plumblossomcat.torbox.ui.components.PanelDivider
import io.github.plumblossomcat.torbox.ui.components.PanelRow
import io.github.plumblossomcat.torbox.ui.components.SectionHeader
import io.github.plumblossomcat.torbox.ui.components.SheetHeader
import io.github.plumblossomcat.torbox.ui.components.SkeletonRows
import io.github.plumblossomcat.torbox.ui.components.Tag
import io.github.plumblossomcat.torbox.ui.components.TbButton
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.components.TbLink
import io.github.plumblossomcat.torbox.ui.components.TbSheet
import io.github.plumblossomcat.torbox.ui.components.TbTextField
import io.github.plumblossomcat.torbox.ui.components.TopBar
import io.github.plumblossomcat.torbox.ui.components.WhileVisible
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val ACTIVE_JOB = setOf("pending", "queued", "uploading", "processing", "running", "started", "downloading")

val IntegrationJobDto.isActive: Boolean get() = status.lowercase() in ACTIVE_JOB
val IntegrationJobDto.fraction: Float get() = (if (progress > 1.0) progress / 100.0 else progress).toFloat().coerceIn(0f, 1f)

fun IntegrationProvider.keyHelp(): Pair<String, String>? = when (this) {
    IntegrationProvider.GOFILE -> "Find your GoFile account token" to "https://gofile.io/myProfile"
    IntegrationProvider.ONEFICHIER -> "Find your 1fichier API key" to "https://1fichier.com/console/params.pl"
    IntegrationProvider.PIXELDRAIN -> "Create a Pixeldrain API key" to "https://pixeldrain.com/user/api_keys"
    else -> null
}

class CloudViewModel(container: AppContainer) :
    LoadViewModel<List<IntegrationJobDto>>(container, { client.integrationJobs().sortedByDescending { Timestamps.parse(it.createdAt) } }) {

    val tokens: StateFlow<Map<IntegrationProvider, String>> = container.settings.integrationTokens()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun save(provider: IntegrationProvider, token: String?) {
        viewModelScope.launch {
            container.settings.setIntegrationToken(provider, token)
            message.value = if (token.isNullOrBlank()) "${provider.label} disconnected" else "${provider.label} connected"
        }
    }

    fun cancel(job: IntegrationJobDto) = mutate("Upload cancelled") { client.cancelIntegrationJob(job.id) }

    fun oauthUrl(provider: IntegrationProvider): String? = container.client.oauthUrl(provider)
}

@Composable
fun CloudScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val vm: CloudViewModel = viewModel { CloudViewModel(container) }
    val load by vm.load.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val tokens by vm.tokens.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val dark = Tb.colors.isDark
    LaunchedEffect(message) {
        message?.let {
            toaster.show(it)
            vm.message.value = null
        }
    }
    val active = (load as? Load.Ready)?.value?.any { it.isActive } == true
    WhileVisible(5_000, enabled = active) { vm.reload() }
    var oauth by remember { mutableStateOf<IntegrationProvider?>(null) }

    CloudContent(
        load = load,
        refreshing = refreshing,
        tokens = tokens,
        onBack = onBack,
        onRefresh = vm::refresh,
        onSave = vm::save,
        onCancel = vm::cancel,
        onConnect = { provider ->
            val url = vm.oauthUrl(provider) ?: return@CloudContent
            if (provider == IntegrationProvider.GOOGLE_DRIVE) {
                // Google refuses sign-in inside embedded web views, so this one goes through the browser.
                container.device.browse(url, dark)
            } else {
                oauth = provider
            }
        },
        onBrowse = { container.device.browse(it, dark) },
        onOpenJob = { job -> job.downloadUrl?.let { container.device.browse(it, dark) } },
    )

    oauth?.let { provider ->
        OAuthDialog(
            url = vm.oauthUrl(provider).orEmpty(),
            provider = provider,
            onToken = {
                oauth = null
                vm.save(provider, it)
            },
            onDismiss = { oauth = null },
        )
    }
}

@Composable
fun CloudContent(
    load: Load<List<IntegrationJobDto>>,
    refreshing: Boolean,
    tokens: Map<IntegrationProvider, String>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSave: (IntegrationProvider, String?) -> Unit,
    onCancel: (IntegrationJobDto) -> Unit,
    onConnect: (IntegrationProvider) -> Unit,
    onBrowse: (String) -> Unit,
    onOpenJob: (IntegrationJobDto) -> Unit,
) {
    var editing by remember { mutableStateOf<IntegrationProvider?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Cloud uploads", onBack)
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
                item(key = "intro") {
                    Text(
                        "Send finished downloads straight to your cloud storage. TorBox does the uploading, so your phone doesn't have to.",
                        style = Tb.type.body.copy(color = Tb.colors.inkMuted),
                        modifier = Modifier.padding(start = Gutter, end = Gutter, bottom = 20.dp),
                    )
                }
                item(key = "accountsHeader") { SectionHeader("Accounts") }
                item(key = "accounts") {
                    Panel {
                        IntegrationProvider.entries.forEachIndexed { i, provider ->
                            if (i > 0) PanelDivider()
                            val connected = tokens.containsKey(provider)
                            PanelRow(
                                title = provider.label,
                                subtitle = when {
                                    connected -> "Connected"
                                    provider.tokenRequired -> "Not connected"
                                    else -> "Optional — uploads anonymously without a key"
                                },
                                icon = TbIcons.Cloud,
                                iconTint = if (connected) Tb.colors.signalInk else Tb.colors.inkMuted,
                                onClick = { editing = provider },
                            )
                        }
                    }
                }
                item(key = "jobsHeader") {
                    Spacer(Modifier.height(28.dp))
                    SectionHeader("Uploads")
                }
                when (load) {
                    Load.Loading -> item(key = "loading") { SkeletonRows(3) }
                    is Load.Failed -> item(key = "failed") { ErrorState(load.message, onRefresh) }
                    is Load.Ready -> if (load.value.isEmpty()) {
                        item(key = "empty") {
                            EmptyState("No uploads yet", "Open any finished download and choose Cloud to send it somewhere.")
                        }
                    } else {
                        items(load.value, key = { it.id }) { job -> JobRow(job, onCancel, onOpenJob) }
                    }
                }
                item(key = "end") { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    editing?.let { provider ->
        ProviderSheet(
            provider = provider,
            current = tokens[provider],
            onDismiss = { editing = null },
            onSave = {
                editing = null
                onSave(provider, it)
            },
            onConnect = {
                if (provider != IntegrationProvider.GOOGLE_DRIVE) editing = null
                onConnect(provider)
            },
            onBrowse = onBrowse,
        )
    }
}

@Composable
private fun JobRow(job: IntegrationJobDto, onCancel: (IntegrationJobDto) -> Unit, onOpen: (IntegrationJobDto) -> Unit) {
    val c = Tb.colors
    val failed = job.status.contains("fail", true) || job.status.contains("error", true)
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        IntegrationProvider.entries.firstOrNull { it.path.equals(job.integration, true) || it.oauthProvider.equals(job.integration, true) }?.label
                            ?: job.integration?.replaceFirstChar { it.uppercase() } ?: "Upload",
                        style = Tb.type.subtitle,
                    )
                    Spacer(Modifier.width(8.dp))
                    Tag(job.status.ifBlank { "unknown" }, color = when {
                        failed -> c.danger
                        job.isActive -> c.signalInk
                        else -> null
                    })
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        job.type?.replaceFirstChar { it.uppercase() },
                        if (job.zip) "zip" else "file",
                        Format.relative(Timestamps.parse(job.createdAt)),
                    ).joinToString("  ·  "),
                    style = Tb.type.monoSmall,
                )
            }
            if (job.isActive) {
                TbIconButton(TbIcons.Close, "Cancel upload", { onCancel(job) }, tint = c.inkMuted)
            } else if (!job.downloadUrl.isNullOrBlank()) {
                TbIconButton(TbIcons.External, "Open uploaded file", { onOpen(job) }, tint = c.inkMuted)
            }
        }
        if (job.isActive) {
            Spacer(Modifier.height(10.dp))
            Meter(job.fraction.takeIf { it > 0f }, color = c.meterFor(io.github.plumblossomcat.torbox.core.model.TransferState.DOWNLOADING))
        }
        job.detail?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = Tb.type.bodySmall.copy(color = if (failed) c.danger else c.inkMuted), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
    Hairline(Modifier.padding(horizontal = Gutter))
}

@Composable
private fun ProviderSheet(
    provider: IntegrationProvider,
    current: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
    onConnect: () -> Unit,
    onBrowse: (String) -> Unit,
) {
    var token by remember { mutableStateOf(current.orEmpty()) }
    TbSheet(onDismiss) {
        SheetHeader(provider.label, if (current != null) "Connected" else "Not connected")
        Column(Modifier.padding(horizontal = Gutter)) {
            if (provider.oauthProvider != null) {
                Text(
                    if (provider == IntegrationProvider.GOOGLE_DRIVE) {
                        "Sign in with Google in your browser. When TorBox shows the success page, copy the token value from its address (after “token=”) and paste it here."
                    } else {
                        "Sign in with ${provider.label} and the connection is saved automatically."
                    },
                    style = Tb.type.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                TbButton("Connect ${provider.label}", onConnect, style = ButtonStyle.Primary, icon = TbIcons.External, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                Eyebrow("Or paste a token")
                Spacer(Modifier.height(8.dp))
            } else {
                Text(
                    "Paste your ${provider.label} key to upload into your own account. Without one, files are uploaded anonymously.",
                    style = Tb.type.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
            }
            TbTextField(
                value = token,
                onValueChange = { token = it.trim() },
                placeholder = if (provider.oauthProvider != null) "Access token" else "API key",
                mono = true,
                visualTransformation = PasswordVisualTransformation('•'),
            )
            provider.keyHelp()?.let { (label, url) ->
                TbLink(label, { onBrowse(url) }, icon = TbIcons.External, color = Tb.colors.inkMuted)
            }
            Spacer(Modifier.height(16.dp))
            Row {
                if (current != null) {
                    TbButton("Disconnect", { onSave(null) }, style = ButtonStyle.Danger, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                }
                TbButton("Save", { onSave(token) }, enabled = token.isNotBlank() && token != current, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Hosts TorBox's white-label OAuth flow and captures the token from the final redirect to
 * `torbox.app/{provider}/success?token=…`.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OAuthDialog(url: String, provider: IntegrationProvider, onToken: (String) -> Unit, onDismiss: () -> Unit) {
    var failed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Tb.colors.background)) {
            TopBar("Connect ${provider.label}", onBack = onDismiss)
            Box(Modifier.fillMaxSize()) {
                if (failed) {
                    ErrorState("The sign-in page couldn't load. Check your connection and try again.", null)
                } else {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = object : WebViewClient() {
                                    private fun capture(target: String?): Boolean {
                                        val uri = target?.let(Uri::parse) ?: return false
                                        val token = uri.getQueryParameter("token")
                                        if (uri.host?.endsWith("torbox.app") == true && uri.path?.contains("success") == true && !token.isNullOrBlank()) {
                                            onToken(token)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean =
                                        capture(request?.url?.toString())

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        capture(url)
                                    }

                                    override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                        if (request?.isForMainFrame == true) failed = true
                                    }
                                }
                                loadUrl(url)
                            }
                        },
                    )
                }
            }
        }
    }
}
