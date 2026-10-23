package io.github.plumblossomcat.torbox.ui.screens.add

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.plumblossomcat.torbox.core.api.PostProcessing
import io.github.plumblossomcat.torbox.core.api.SeedPreference
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.util.Format
import io.github.plumblossomcat.torbox.ui.LocalContainer
import io.github.plumblossomcat.torbox.ui.components.ButtonStyle
import io.github.plumblossomcat.torbox.ui.components.Gutter
import io.github.plumblossomcat.torbox.ui.components.Hairline
import io.github.plumblossomcat.torbox.ui.components.Labeled
import io.github.plumblossomcat.torbox.ui.components.LocalToaster
import io.github.plumblossomcat.torbox.ui.components.OptionRow
import io.github.plumblossomcat.torbox.ui.components.Segmented
import io.github.plumblossomcat.torbox.ui.components.TbButton
import io.github.plumblossomcat.torbox.ui.components.TbIconButton
import io.github.plumblossomcat.torbox.ui.components.TbLink
import io.github.plumblossomcat.torbox.ui.components.TbTextField
import io.github.plumblossomcat.torbox.ui.components.ToggleRow
import io.github.plumblossomcat.torbox.ui.components.TopBar
import io.github.plumblossomcat.torbox.ui.theme.Tb
import io.github.plumblossomcat.torbox.ui.theme.TbIcons

@Composable
fun AddScreen(
    initialKind: TransferKind?,
    onBack: () -> Unit,
    onOpened: (TransferKind, Long) -> Unit,
    onLibrary: () -> Unit,
    onQueued: () -> Unit,
    onHosters: () -> Unit,
) {
    val container = LocalContainer.current
    val vm: AddViewModel = viewModel { AddViewModel(container, initialKind) }
    val form by vm.form.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(result) {
        when (val r = result) {
            null -> Unit
            is AddResult.Opened -> {
                toaster.show("Added to TorBox")
                onOpened(r.kind, r.id)
            }
            is AddResult.Queued -> {
                toaster.show(if (r.count == 1) "Added to your queue" else "${r.count} added to your queue", "View", onQueued)
                onBack()
            }
            is AddResult.Many -> {
                val text = if (r.failed == 0) "Added ${r.added} ${r.kind.pluralLabel.lowercase()}" else "Added ${r.added}, ${r.failed} failed: ${r.firstError}"
                toaster.show(text)
                onLibrary()
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickFile(uri, context.contentResolver)
    }

    AddContent(
        form = form,
        onBack = onBack,
        onKind = vm::setKind,
        onText = vm::setText,
        onPaste = { clipboard.getText()?.text?.let { pasted -> vm.setText(if (form.text.isBlank()) pasted else form.text.trimEnd() + "\n" + pasted) } },
        onPickFile = {
            picker.launch(
                if (form.kind == TransferKind.USENET) arrayOf("application/x-nzb", "application/octet-stream", "text/xml", "*/*")
                else arrayOf("application/x-bittorrent", "application/octet-stream", "*/*"),
            )
        },
        onClearFile = vm::clearFile,
        onName = vm::setName,
        onPassword = vm::setPassword,
        onSeed = vm::setSeed,
        onAllowZip = vm::setAllowZip,
        onQueued = vm::setQueued,
        onPostProcessing = vm::setPostProcessing,
        onSubmit = vm::submit,
        onHosters = onHosters,
    )
}

@Composable
fun AddContent(
    form: AddForm,
    onBack: () -> Unit,
    onKind: (TransferKind) -> Unit,
    onText: (String) -> Unit,
    onPaste: () -> Unit,
    onPickFile: () -> Unit,
    onClearFile: () -> Unit,
    onName: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSeed: (SeedPreference) -> Unit,
    onAllowZip: (Boolean) -> Unit,
    onQueued: (Boolean) -> Unit,
    onPostProcessing: (PostProcessing) -> Unit,
    onSubmit: () -> Unit,
    onHosters: () -> Unit,
) {
    val c = Tb.colors
    var showPassword by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopBar("New download", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Segmented(
                options = TransferKind.entries.map { it.label },
                selected = TransferKind.entries.indexOf(form.kind),
                onSelect = { onKind(TransferKind.entries[it]) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Gutter),
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                if (form.file == null) {
                    TbTextField(
                        value = form.text,
                        onValueChange = onText,
                        label = when (form.kind) {
                            TransferKind.TORRENT -> "Magnet links or info hashes"
                            TransferKind.USENET -> "NZB links"
                            TransferKind.WEB -> "Links"
                        },
                        placeholder = when (form.kind) {
                            TransferKind.TORRENT -> "magnet:?xt=urn:btih:…"
                            TransferKind.USENET -> "https://indexer.example/getnzb/…"
                            TransferKind.WEB -> "https://"
                        },
                        supporting = "One per line to add several at once.",
                        mono = true,
                        singleLine = false,
                        minLines = 4,
                        maxLines = 10,
                        error = form.error,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        trailing = {
                            Column(Modifier.padding(top = 4.dp)) {
                                TbIconButton(TbIcons.Paste, "Paste", onPaste, tint = c.inkMuted, size = 40.dp)
                            }
                        },
                    )
                    CacheLine(form.cache)
                }

                if (form.kind != TransferKind.WEB) {
                    FilePickerRow(form, onPickFile, onClearFile)
                }

                if (!form.isBatch) {
                    TbTextField(
                        value = form.name,
                        onValueChange = onName,
                        label = "Name (optional)",
                        placeholder = "Use the original name",
                    )
                }

                if (form.kind != TransferKind.TORRENT) {
                    TbTextField(
                        value = form.password,
                        onValueChange = onPassword,
                        label = if (form.kind == TransferKind.USENET) "Archive password (optional)" else "Link password (optional)",
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation('•'),
                        trailing = {
                            TbIconButton(if (showPassword) TbIcons.EyeOff else TbIcons.Eye, if (showPassword) "Hide password" else "Show password", { showPassword = !showPassword }, tint = c.inkMuted, size = 40.dp)
                        },
                    )
                }

                if (form.kind == TransferKind.TORRENT) {
                    Labeled("Seeding", supporting = when (form.seed) {
                        SeedPreference.AUTO -> "Follows the seeding setting on your TorBox account."
                        SeedPreference.ALWAYS -> "Keeps uploading to others after it finishes."
                        SeedPreference.NEVER -> "Stops as soon as the download completes."
                    }) {
                        Segmented(
                            options = listOf("Default", "Always", "Never"),
                            selected = SeedPreference.entries.indexOf(form.seed),
                            onSelect = { onSeed(SeedPreference.entries[it]) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (form.kind == TransferKind.USENET) {
                    Labeled("Post-processing") {
                        Column {
                            PostProcessing.entries.forEach { option ->
                                OptionRow(option.label, form.postProcessing == option, { onPostProcessing(option) }, subtitle = option.detail)
                            }
                        }
                    }
                }

                Column {
                    Hairline()
                    if (form.kind == TransferKind.TORRENT) {
                        ToggleRow("Allow zipping", form.allowZip, onAllowZip, subtitle = "Lets you download everything as a single zip.")
                        Hairline()
                    }
                    ToggleRow("Add to queue", form.asQueued, onQueued, subtitle = "Hold it until you start it, instead of starting now.")
                    Hairline()
                }

                if (form.kind == TransferKind.WEB) {
                    TbLink("Which hosts are supported?", onHosters, icon = TbIcons.ChevronRight, color = c.inkMuted)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.background)
                .navigationBarsPadding(),
        ) {
            Hairline()
            if (form.file != null && form.error != null) {
                Text(form.error, style = Tb.type.bodySmall.copy(color = c.danger), modifier = Modifier.padding(start = Gutter, end = Gutter, top = 12.dp))
            }
            TbButton(
                text = form.progress ?: when {
                    form.submitting -> "Adding…"
                    form.isBatch -> "Add ${form.entries.size} to TorBox"
                    else -> "Add to TorBox"
                },
                onClick = onSubmit,
                loading = form.submitting,
                enabled = form.file != null || form.entries.isNotEmpty(),
                style = ButtonStyle.Signal,
                icon = TbIcons.Add,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Gutter, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun CacheLine(status: CacheStatus) {
    val c = Tb.colors
    AnimatedVisibility(status != CacheStatus.Idle) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 0.dp)) {
            when (status) {
                CacheStatus.Checking -> {
                    CircularProgressIndicator(Modifier.size(12.dp), color = c.inkMuted, strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Checking TorBox's cache…", style = Tb.type.bodySmall)
                }
                is CacheStatus.Cached -> {
                    Icon(TbIcons.Zap, null, tint = c.signalInk, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(AddViewModel.describe(status), style = Tb.type.bodySmall.copy(color = c.ink))
                }
                is CacheStatus.NotCached -> {
                    Icon(TbIcons.Clock, null, tint = c.inkMuted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (status.of > 1) "None are cached yet — TorBox will download them first" else "Not cached yet — TorBox will download it first",
                        style = Tb.type.bodySmall,
                    )
                }
                CacheStatus.Idle -> Unit
            }
        }
    }
}

@Composable
private fun FilePickerRow(form: AddForm, onPick: () -> Unit, onClear: () -> Unit) {
    val c = Tb.colors
    val ext = if (form.kind == TransferKind.USENET) ".nzb" else ".torrent"
    val file = form.file
    Column {
    if (file == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Hairline(Modifier.weight(1f))
            Text("or", style = Tb.type.eyebrow, modifier = Modifier.padding(horizontal = 12.dp))
            Hairline(Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        TbButton("Choose a $ext file", onPick, style = ButtonStyle.Secondary, icon = TbIcons.Upload, modifier = Modifier.fillMaxWidth())
    } else {
        val shape = RoundedCornerShape(14.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.raised)
                .border(1.dp, c.hairlineStrong, shape)
                .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(c.sunken), contentAlignment = Alignment.Center) {
                Icon(if (form.kind == TransferKind.USENET) TbIcons.Newspaper else TbIcons.Magnet, null, tint = c.ink, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.fileName, style = Tb.type.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.bytes(file.bytes.size.toLong()), style = Tb.type.monoSmall)
            }
            TbIconButton(TbIcons.Close, "Remove file", onClear, tint = c.inkMuted)
        }
    }
    }
}
