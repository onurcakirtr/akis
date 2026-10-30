@file:OptIn(ExperimentalMaterial3Api::class)

package com.akis.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.result.PickVisualMediaRequest
import com.akis.app.ServerIcon
import com.akis.app.ui.theme.Themes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.DeviceAccess
import com.akis.app.FileAccess
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.akis.app.Lang
import com.akis.app.R
import com.akis.app.Prefs
import com.akis.app.ServerState
import com.akis.app.WatchHistory
import com.akis.app.widget.Widgets
import com.akis.app.dlna.MediaLibrary
import com.akis.app.dlna.Subtitles
import com.akis.app.dlna.Uploads
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.akis.app.service.DlnaService

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    val running by ServerState.running.collectAsStateWithLifecycle()
    var editName by remember { mutableStateOf(false) }
    var editPort by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var editPin by remember { mutableStateOf(false) }
    var pickLang by remember { mutableStateOf(false) }
    var pickIcon by remember { mutableStateOf(false) }
    var iconTick by remember { mutableStateOf(0) }
    val iconPreview = remember(s.iconStyle, s.theme, iconTick) { ServerIcon.bitmap(ctx, 96).asImageBitmap() }
    val galleryIcon = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null && ServerIcon.saveCustom(ctx, uri)) {
            Prefs.update { putString("iconStyle", "custom") }
            iconTick++
            pickIcon = false
        }
    }
    var filesGranted by remember { mutableStateOf(FileAccess.granted(ctx)) }
    LifecycleResumeEffect(Unit) {
        filesGranted = FileAccess.granted(ctx)
        onPauseOrDispose { }
    }
    val legacyPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { filesGranted = FileAccess.granted(ctx) }
    fun askFiles() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try { ctx.startActivity(FileAccess.settingsIntent(ctx)) } catch (_: Exception) { }
        } else {
            legacyPerm.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val devices by DeviceAccess.devices.collectAsStateWithLifecycle()
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                Prefs.update { putString("uploadFolder", uri.toString()) }
            } catch (_: Exception) {
            }
        }
    }
    val uploadTargetName by produceState<String?>(null, s.uploadFolder) {
        value = if (s.uploadFolder.isEmpty()) null else withContext(Dispatchers.IO) { MediaLibrary.treeName(ctx, s.uploadFolder) }
    }
    val startedAt by ServerState.startedAt.collectAsStateWithLifecycle()
    // Sunucu açıkken port değişirse yeniden başlatma öner
    val portAtStart = remember(startedAt, running) { s.port }
    val needsRestart = running && portAtStart != s.port

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp))
        }

        if (needsRestart) {
            item {
                ExpressiveCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(stringResource(R.string.restart_for_port), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { DlnaService.restart(ctx) }) {
                        Icon(Icons.Rounded.RestartAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.restart))
                    }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.sec_server)) }
        item {
            ExpressiveCard {
                SettingRow(Icons.Rounded.Smartphone, stringResource(R.string.network_name), s.name, onClick = { editName = true }) {
                    Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider()
                SettingRow(
                    Icons.Rounded.Image, stringResource(R.string.icon_title), stringResource(R.string.icon_sub),
                    onClick = { pickIcon = true },
                ) {
                    Image(iconPreview, null, Modifier.size(40.dp).clip(RoundedCornerShape(percent = 28)))
                }
                Divider()
                SettingRow(Icons.Rounded.Router, stringResource(R.string.port), s.port.toString(), onClick = { editPort = true }) {
                    Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider()
                SettingRow(
                    Icons.Rounded.Language, stringResource(R.string.web_ui), stringResource(R.string.web_ui_sub),
                    onClick = { Prefs.update { putBoolean("webUi", !s.webUi) } },
                ) { Switch(s.webUi, { v -> Prefs.update { putBoolean("webUi", v) } }) }
            }
        }

        item { SectionTitle(stringResource(R.string.sec_behavior)) }
        item {
            ExpressiveCard {
                SettingRow(
                    Icons.Rounded.Bolt, stringResource(R.string.keep_awake), stringResource(R.string.keep_awake_sub),
                    onClick = { Prefs.update { putBoolean("keepAwake", !s.keepAwake) } },
                ) { Switch(s.keepAwake, { v -> Prefs.update { putBoolean("keepAwake", v) } }) }
                Divider()
                SettingRow(
                    Icons.Rounded.RestartAlt, stringResource(R.string.auto_start), stringResource(R.string.auto_start_sub),
                    onClick = { Prefs.update { putBoolean("autoStart", !s.autoStart) } },
                ) { Switch(s.autoStart, { v -> Prefs.update { putBoolean("autoStart", v) } }) }
            }
        }

        item { SectionTitle(stringResource(R.string.sec_features)) }
        item {
            ExpressiveCard {
                FeatureRow(
                    Icons.Rounded.VisibilityOff, stringResource(R.string.f_hidden),
                    stringResource(R.string.f_hidden_sub),
                    s.hideFolders,
                ) { Prefs.set("hideFolders", it) }
                FeatureRow(
                    Icons.Rounded.ClosedCaption, stringResource(R.string.f_subs),
                    stringResource(R.string.f_subs_sub),
                    s.subtitles, divider = true,
                    extra = {
                        Text(stringResource(R.string.preferred_lang), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Segmented(
                            options = Subtitles.CHOICES.map { it to it.uppercase() },
                            selected = s.subLang,
                        ) { v -> Prefs.update { putString("subLang", v) } }
                    },
                ) { Prefs.set("subtitles", it) }
                FeatureRow(
                    Icons.Rounded.Search, stringResource(R.string.f_search),
                    stringResource(R.string.f_search_sub),
                    s.search, divider = true,
                ) { Prefs.set("search", it) }
                FeatureRow(
                    Icons.Rounded.History, stringResource(R.string.f_history),
                    stringResource(R.string.f_history_sub),
                    s.history, divider = true,
                    extra = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.history_marks), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(s.historyMarks, { v -> Prefs.set("historyMarks", v) })
                        }
                        FilledTonalButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Rounded.DeleteSweep, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.history_clear))
                        }
                    },
                ) { Prefs.set("history", it) }
                FeatureRow(
                    Icons.Rounded.VerifiedUser, stringResource(R.string.f_approval),
                    stringResource(R.string.f_approval_sub),
                    s.deviceApproval, divider = true,
                    extra = {
                        if (devices.isEmpty()) {
                            Text(stringResource(R.string.no_devices), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        devices.forEach { d ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${d.ip} • " + stringResource(if (d.allowed) R.string.device_allowed else R.string.device_blocked),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (d.allowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                    )
                                }
                                TextButton(onClick = { DeviceAccess.forget(d.ip) }) { Text(stringResource(R.string.forget)) }
                            }
                        }
                    },
                ) { Prefs.set("deviceApproval", it) }
                FeatureRow(
                    Icons.Rounded.Password, stringResource(R.string.f_pin),
                    stringResource(R.string.f_pin_sub),
                    s.webPin.isNotEmpty(), divider = true,
                    extra = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.pin_value, s.webPin), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editPin = true }) { Text(stringResource(R.string.change)) }
                        }
                    },
                ) { on ->
                    if (on) {
                        editPin = true
                    } else {
                        Prefs.update { putString("webPin", "") }
                    }
                }
                FeatureRow(
                    Icons.Rounded.Upload, stringResource(R.string.f_upload),
                    stringResource(R.string.f_upload_sub),
                    s.upload, divider = true,
                    extra = {
                        Text(
                            stringResource(R.string.upload_target, uploadTargetName ?: stringResource(R.string.downloads_dir, Uploads.DEFAULT_DIR)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { uploadPicker.launch(null) }) { Text(stringResource(R.string.choose_folder)) }
                            if (s.uploadFolder.isNotEmpty()) {
                                TextButton(onClick = { Prefs.update { putString("uploadFolder", "") } }) { Text(stringResource(R.string.default_value)) }
                            }
                        }
                        if (!s.webUi) {
                            Text(stringResource(R.string.upload_needs_web), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (s.webPin.isEmpty()) {
                            Text(stringResource(R.string.upload_pin_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                ) { Prefs.set("upload", it) }
                FeatureRow(
                    Icons.Rounded.FolderOpen, stringResource(R.string.f_allfiles),
                    stringResource(R.string.f_allfiles_sub),
                    s.allFilesWeb || s.allFilesUpnp, divider = true,
                    extra = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.allfiles_web), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(s.allFilesWeb, { v -> Prefs.set("allFilesWeb", v) })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.allfiles_upnp), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(s.allFilesUpnp, { v -> Prefs.set("allFilesUpnp", v) })
                        }
                        if (filesGranted) {
                            Text(stringResource(R.string.allfiles_granted), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text(stringResource(R.string.allfiles_need), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            FilledTonalButton(onClick = { askFiles() }) { Text(stringResource(R.string.allfiles_grant)) }
                        }
                        if (!s.deviceApproval && s.webPin.isEmpty()) {
                            Text(stringResource(R.string.allfiles_warn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                ) { on ->
                    Prefs.update { putBoolean("allFilesWeb", on); putBoolean("allFilesUpnp", on) }
                    if (on && !filesGranted) askFiles()
                }
                FeatureRow(
                    Icons.Rounded.Widgets, stringResource(R.string.f_widget),
                    stringResource(R.string.f_widget_sub),
                    s.widget, divider = true,
                ) { on ->
                    Prefs.set("widget", on)
                    Widgets.setEnabled(ctx, on)
                }
                FeatureRow(
                    Icons.Rounded.Notifications, stringResource(R.string.f_notif),
                    stringResource(R.string.f_notif_sub),
                    s.notifDetails, divider = true,
                ) { Prefs.set("notifDetails", it) }
            }
        }

        item { SectionTitle(stringResource(R.string.sec_appearance)) }
        item {
            ExpressiveCard {
                SettingRow(
                    Icons.Rounded.Translate, stringResource(R.string.language),
                    Lang.SUPPORTED.firstOrNull { it.first == Lang.current }?.second ?: stringResource(R.string.lang_system),
                    onClick = { pickLang = true },
                ) { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Divider()
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Palette, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                ThemeGrid(s.theme, s.darkMode) { v -> Prefs.update { putString("theme", v) } }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.DarkMode, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.dark_mode), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                if (Themes.byId(s.theme).darkOnly) {
                    Text(
                        stringResource(R.string.theme_dark_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Segmented(
                        options = listOf("system" to stringResource(R.string.mode_system), "light" to stringResource(R.string.mode_light), "dark" to stringResource(R.string.mode_dark)),
                        selected = s.darkMode,
                    ) { v -> Prefs.update { putString("darkMode", v) } }
                }
            }
        }

        item { SectionTitle(stringResource(R.string.sec_about)) }
        item {
            ExpressiveCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Info, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Akış 1.0", style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.about_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (pickIcon) {
        AlertDialog(
            onDismissRequest = { pickIcon = false },
            title = { Text(stringResource(R.string.icon_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val presets = ServerIcon.PRESETS
                    presets.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { pr ->
                                val img = remember(pr.id, s.theme) { ServerIcon.draw(ctx, pr.id, 96).asImageBitmap() }
                                val on = s.iconStyle == pr.id
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    androidx.compose.material3.Surface(
                                        onClick = { Prefs.update { putString("iconStyle", pr.id) }; pickIcon = false },
                                        shape = RoundedCornerShape(percent = 28),
                                        border = if (on) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                                    ) { Image(img, null, Modifier.size(56.dp)) }
                                    Text(stringResource(pr.name), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    FilledTonalButton(onClick = {
                        galleryIcon.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.Image, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.icon_pick))
                    }
                    Text(stringResource(R.string.icon_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { pickIcon = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (pickLang) {
        AlertDialog(
            onDismissRequest = { pickLang = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    (listOf("" to stringResource(R.string.lang_system)) + Lang.SUPPORTED).forEach { (tag, name) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = Lang.current == tag, onClick = { pickLang = false; Lang.set(tag) })
                            Spacer(Modifier.width(8.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickLang = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_q)) },
            text = { Text(stringResource(R.string.clear_text)) },
            confirmButton = { TextButton(onClick = { WatchHistory.clear(); confirmClear = false }) { Text(stringResource(R.string.clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editPin) {
        TextDialog(stringResource(R.string.pin_dialog), s.webPin, KeyboardType.NumberPassword, onDismiss = { editPin = false }) { v ->
            val pin = v.trim()
            if (pin.length in 4..6 && pin.all { it.isDigit() }) {
                Prefs.update { putString("webPin", pin) }
                editPin = false
            }
        }
    }
    if (editName) {
        TextDialog(stringResource(R.string.network_name), s.name, KeyboardType.Text, onDismiss = { editName = false }) { v ->
            Prefs.update { putString("name", v.trim()) }
            editName = false
        }
    }
    if (editPort) {
        TextDialog(stringResource(R.string.port_dialog), s.port.toString(), KeyboardType.Number, onDismiss = { editPort = false }) { v ->
            val p = v.trim().toIntOrNull()
            if (p != null && p in 1024..65535) {
                Prefs.update { putInt("port", p) }
                editPort = false
            }
        }
    }
}

@Composable
private fun Divider() = HorizontalDivider(
    Modifier.padding(vertical = 6.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
)

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = androidx.compose.ui.graphics.Color.Transparent,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Özellik aç/kapa satırı; açıkken altındaki ek ayarlar (extra) yaylı animasyonla açılır. */
@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    divider: Boolean = false,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    if (divider) Divider()
    SettingRow(icon, title, subtitle, onClick = { onChange(!checked) }) { Switch(checked, onChange) }
    if (extra != null) {
        AnimatedVisibility(
            visible = checked,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 500f)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = 700f)) + fadeOut(),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 54.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = extra)
        }
    }
}

/** Tema seçimi: her tema kendi renkleriyle küçük bir önizleme kartı olarak, 3 sütunda. */
@Composable
private fun ThemeGrid(selected: String, darkMode: String, onSelect: (String) -> Unit) {
    val ctx = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Themes.ALL.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val cs = t.scheme(ctx, t.isDark(darkMode, systemDark))
                    val on = t.id == selected
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        androidx.compose.material3.Surface(
                            onClick = { onSelect(t.id) },
                            shape = t.shapes.medium,
                            color = cs.background,
                            border = androidx.compose.foundation.BorderStroke(
                                if (on) 3.dp else 1.dp,
                                if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth().height(84.dp),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(14.dp).clip(t.badge).background(cs.primary))
                                    Spacer(Modifier.width(6.dp))
                                    Box(Modifier.height(6.dp).weight(1f).clip(t.shapes.extraSmall).background(cs.onSurface.copy(alpha = 0.7f)))
                                }
                                Box(Modifier.fillMaxWidth().height(20.dp).clip(t.shapes.small).background(cs.surfaceContainerHigh))
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.height(12.dp).weight(1f).clip(t.shapes.extraSmall).background(cs.primaryContainer))
                                    Box(Modifier.height(12.dp).weight(1f).clip(t.shapes.extraSmall).background(cs.tertiaryContainer))
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(t.name),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    keyboard: KeyboardType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
