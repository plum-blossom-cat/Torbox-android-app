package io.github.plumblossomcat.torbox.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.AppContainer
import io.github.plumblossomcat.torbox.R
import io.github.plumblossomcat.torbox.core.api.TorBoxException
import io.github.plumblossomcat.torbox.data.friendly
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.ButtonStyle
import io.github.plumblossomcat.torbox.ui.components.Eyebrow
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Notice
import io.github.plumblossomcat.torbox.ui.components.TbButton
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.components.TbLink
import io.github.plumblossomcat.torbox.ui.components.TbTextField
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val TORBOX_SETTINGS_URL = "https://torbox.app/settings"
const val TORBOX_SITE_URL = "https://torbox.app"

data class LoginState(
    val key: String = "",
    val revealed: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun onKey(value: String) = _state.update { it.copy(key = value.trim(), error = null) }
    fun toggleReveal() = _state.update { it.copy(revealed = !it.revealed) }

    fun submit() {
        val key = _state.value.key.trim()
        if (key.isEmpty()) {
            _state.update { it.copy(error = "Paste your API key to continue.") }
            return
        }
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val user = container.client.meWithToken(key)
                container.account.set(user)
                container.session.signIn(key)
            } catch (e: TorBoxException.Api) {
                val message = if (e.isAuthError || e.httpStatus in 400..499) {
                    "TorBox didn't accept that key. Copy it again from your TorBox settings."
                } else {
                    e.friendly()
                }
                _state.update { it.copy(loading = false, error = message) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.friendly()) }
            }
        }
    }
}

@Composable
fun LoginScreen(reason: String?) {
    val container = LocalContainer.current
    val vm: LoginViewModel = viewModel { LoginViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val dark = Tb.colors.isDark
    LoginContent(
        state = state,
        reason = reason,
        onKey = vm::onKey,
        onPaste = { clipboard.getText()?.text?.let(vm::onKey) },
        onToggleReveal = vm::toggleReveal,
        onSubmit = vm::submit,
        onFindKey = { container.device.browse(TORBOX_SETTINGS_URL, dark) },
        onCreateAccount = { container.device.browse(TORBOX_SITE_URL, dark) },
    )
}

@Composable
fun LoginContent(
    state: LoginState,
    reason: String?,
    onKey: (String) -> Unit,
    onPaste: () -> Unit,
    onToggleReveal: () -> Unit,
    onSubmit: () -> Unit,
    onFindKey: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val c = Tb.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.padding(horizontal = Gutter, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF141412))
                    .border(1.dp, if (c.isDark) c.hairlineStrong else Color.Transparent, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(30.dp))
            }
            Spacer(Modifier.width(10.dp))
            Eyebrow("TorBox", color = c.ink)
        }

        Spacer(Modifier.height(56.dp))

        Column(Modifier.padding(horizontal = Gutter)) {
            Text(
                buildAnnotatedString {
                    append("Your library,\n")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = c.signalInk)) { append("anywhere.") }
                },
                style = Tb.type.display,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                "Sign in with your TorBox API key. It's stored encrypted on this device and only ever sent to TorBox.",
                style = Tb.type.body.copy(color = c.inkMuted),
            )
        }

        Spacer(Modifier.height(36.dp))

        if (reason != null) {
            Notice(reason)
            Spacer(Modifier.height(20.dp))
        }

        Column(Modifier.padding(horizontal = Gutter)) {
            TbTextField(
                value = state.key,
                onValueChange = onKey,
                label = "API key",
                placeholder = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                mono = true,
                error = state.error,
                enabled = !state.loading,
                visualTransformation = if (state.revealed) VisualTransformation.None else PasswordVisualTransformation('•'),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                trailing = {
                    TbIconButton(
                        if (state.revealed) TbIcons.EyeOff else TbIcons.Eye,
                        if (state.revealed) "Hide key" else "Show key",
                        onToggleReveal,
                        tint = c.inkMuted,
                        size = 40.dp,
                    )
                    TbIconButton(TbIcons.Paste, "Paste from clipboard", onPaste, tint = c.inkMuted, size = 40.dp)
                },
            )
            Spacer(Modifier.height(20.dp))
            TbButton(
                text = if (state.loading) "Checking key…" else "Continue",
                onClick = onSubmit,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
                style = ButtonStyle.Primary,
            )
            Spacer(Modifier.height(28.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TbLink("Where do I find my API key?", onFindKey, icon = TbIcons.External)
                TbLink("New to TorBox? Create an account", onCreateAccount, icon = TbIcons.External, color = c.inkMuted)
            }
        }
        Spacer(Modifier.height(48.dp))
        Text(
            "Your key gives full access to your TorBox account. You can reset it from TorBox settings at any time.",
            style = Tb.type.bodySmall.copy(color = c.inkFaint),
            modifier = Modifier.padding(horizontal = Gutter, vertical = 24.dp),
        )
    }
}
