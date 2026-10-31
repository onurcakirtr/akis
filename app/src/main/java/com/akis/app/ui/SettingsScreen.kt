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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Videocam
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
import com.akis.app.Admin
import com.akis.app.Totp
import com.akis.app.Tunnel
import com.akis.app.RemoteAuth
import androidx.compose.ui.graphics.Color
import com.akis.app.DeviceAccess
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val tunnelState by Tunnel.state.collectAsStateWithLifecycle()
    val tunnelUrl by Tunnel.url.collectAsStateWithLifecycle()
    val tunnelError by Tunnel.lastError.collectAsStateWithLifecycle()
    var tokenDraft by remember(s.remoteToken) { mutableStateOf(s.remoteToken) }
    var hostDraft by remember(s.remoteHost) { mutableStateOf(s.remoteHost) }
    var remoteSessions by remember { mutableStateOf(RemoteAuth.count) }
    var editRemotePw by remember { mutableStateOf(false) }
    var remoteAfterPw by remember { mutableStateOf(false) }
    var remoteConfirm by remember { mutableStateOf(false) }
    var totpSetup by remember { mutableStateOf<String?>(null) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) Prefs.set("camera", true) }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) Prefs.set("mic", true) }
    var editAdminPw by remember { mutableStateOf(false) }
    val adminSession by Admin.active.collectAsStateWithLifecycle()
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
                                        "${d.ip} • " + if (d.until > 0) stringResource(R.string.device_banned_until, untilText(d.until))
                                        else stringResource(if (d.allowed) R.string.device_allowed else R.string.device_blocked),
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
                    Icons.Rounded.AdminPanelSettings, stringResource(R.string.f_admin),
                    stringResource(R.string.f_admin_sub),
                    s.adminOn, divider = true,
                    extra = {
                        Text(
                            adminSession?.let { stringResource(R.string.admin_session, it.name, it.ip) } ?: stringResource(R.string.admin_no_session),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { editAdminPw = true }) { Text(stringResource(R.string.admin_change_pw)) }
                            if (adminSession != null) TextButton(onClick = { Admin.endSession() }) { Text(stringResource(R.string.admin_end)) }
                        }
                        Text(stringResource(R.string.admin_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                ) { on ->
                    if (on && !Admin.hasPassword) {
                        editAdminPw = true
                    } else {
                        Prefs.set("adminOn", on)
                        if (!on) Admin.endSession()
                    }
                }
                FeatureRow(
                    Icons.Rounded.Pin, stringResource(R.string.f_totp), stringResource(R.string.f_totp_sub), s.totpSecret.isNotEmpty(), divider = true,
                ) { on ->
                    if (on) totpSetup = Totp.newSecret() else Prefs.update { putString("totpSecret", "") }
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

        item { SectionTitle(stringResource(R.string.sec_web_features)) }
        item {
            ExpressiveCard {
                FeatureRow(Icons.Rounded.Description, stringResource(R.string.f_docs), stringResource(R.string.f_docs_sub), s.docPreview) { Prefs.set("docPreview", it) }
                FeatureRow(Icons.Rounded.FolderZip, stringResource(R.string.f_zipview), stringResource(R.string.f_zipview_sub), s.zipView, divider = true) { Prefs.set("zipView", it) }
                FeatureRow(Icons.Rounded.Image, stringResource(R.string.f_thumbs), stringResource(R.string.f_thumbs_sub), s.richThumbs, divider = true) { Prefs.set("richThumbs", it) }
                FeatureRow(Icons.Rounded.Keyboard, stringResource(R.string.f_keys), stringResource(R.string.f_keys_sub), s.keysHelp, divider = true) { Prefs.set("keysHelp", it) }
                FeatureRow(Icons.Rounded.Palette, stringResource(R.string.f_look), stringResource(R.string.f_look_sub), s.webLook, divider = true) { Prefs.set("webLook", it) }
                FeatureRow(
                    Icons.Rounded.BatteryStd, stringResource(R.string.f_status), stringResource(R.string.f_status_sub), s.phoneStatus, divider = true,
                    extra = { Text(stringResource(R.string.sensitive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                ) { Prefs.set("phoneStatus", it) }
                FeatureRow(
                    Icons.Rounded.Apps, stringResource(R.string.f_apps), stringResource(R.string.f_apps_sub), s.appsList, divider = true,
                    extra = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.apps_system), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(s.appsSystem, { v -> Prefs.set("appsSystem", v) })
                        }
                        Text(stringResource(R.string.sensitive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                ) { Prefs.set("appsList", it) }
                FeatureRow(
                    Icons.Rounded.Videocam, stringResource(R.string.f_camera), stringResource(R.string.f_camera_sub), s.camera, divider = true,
                    extra = {
                        Segmented(
                            options = listOf("back" to stringResource(R.string.cam_back), "front" to stringResource(R.string.cam_front)),
                            selected = if (s.camFront) "front" else "back",
                        ) { v -> Prefs.set("camFront", v == "front") }
                        Segmented(
                            options = listOf("480" to "480p", "720" to "720p"),
                            selected = s.camHeight.toString(),
                        ) { v -> Prefs.update { putInt("camHeight", v.toInt()) } }
                        Text(stringResource(R.string.cam_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.sensitive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                ) { on ->
                    if (on && ctx.checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        camPerm.launch(android.Manifest.permission.CAMERA)
                    } else {
                        Prefs.set("camera", on)
                    }
                }
                FeatureRow(
                    Icons.Rounded.Smartphone, stringResource(R.string.f_screen), stringResource(R.string.f_screen_sub), s.screen, divider = true,
                    extra = { Text(stringResource(R.string.sensitive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                ) { Prefs.set("screen", it) }
                FeatureRow(
                    Icons.Rounded.Mic, stringResource(R.string.f_mic), stringResource(R.string.f_mic_sub), s.mic, divider = true,
                    extra = {
                        Text(stringResource(R.string.cam_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.sensitive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                ) { on ->
                    if (on && ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        micPerm.launch(android.Manifest.permission.RECORD_AUDIO)
                    } else {
                        Prefs.set("mic", on)
                    }
                }
                FeatureRow(Icons.Rounded.LiveTv, stringResource(R.string.f_live_upnp), stringResource(R.string.f_live_upnp_sub), s.liveUpnp, divider = true) { Prefs.set("liveUpnp", it) }
                FeatureRow(
                    Icons.Rounded.NotificationsActive, stringResource(R.string.f_ring), stringResource(R.string.f_ring_sub), s.ring, divider = true,
                    extra = { if (!s.adminOn) Text(stringResource(R.string.ring_need_admin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) },
                ) { Prefs.set("ring", it) }
                FeatureRow(Icons.Rounded.QrCode2, stringResource(R.string.f_qrcard), stringResource(R.string.f_qrcard_sub), s.qrCard, divider = true) { Prefs.set("qrCard", it) }
                FeatureRow(Icons.Rounded.Wifi, stringResource(R.string.f_wifiqr), stringResource(R.string.f_wifiqr_sub), s.wifiQr, divider = true) { Prefs.set("wifiQr", it) }
                FeatureRow(
                    Icons.Rounded.Notifications, stringResource(R.string.f_notifs), stringResource(R.string.f_notifs_sub), s.notifs, divider = true,
                    extra = {
                        if (!com.akis.app.NotifMirror.granted(ctx)) {
                            Text(stringResource(R.string.notifs_need), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            FilledTonalButton(onClick = {
                                runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                            }) { Text(stringResource(R.string.notifs_grant)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.notifs_title_only), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(s.notifsTitleOnly, { v -> Prefs.set("notifsTitleOnly", v) })
                        }
                        Text(stringResource(R.string.sensitive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                ) { on ->
                    Prefs.set("notifs", on)
                    if (on) com.akis.app.NotifMirror.rebind(ctx) else com.akis.app.NotifMirror.clear()
                    if (on && !com.akis.app.NotifMirror.granted(ctx)) {
                        runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    }
                }
                FeatureRow(Icons.Rounded.Inbox, stringResource(R.string.f_inbox_web), stringResource(R.string.f_inbox_web_sub), s.inboxWeb, divider = true) { Prefs.set("inboxWeb", it) }
                FeatureRow(Icons.Rounded.MoveToInbox, stringResource(R.string.f_filereq), stringResource(R.string.f_filereq_sub), s.fileReq, divider = true) { Prefs.set("fileReq", it) }
                FeatureRow(Icons.Rounded.AddToHomeScreen, stringResource(R.string.f_pwa), stringResource(R.string.f_pwa_sub), s.pwa, divider = true) { Prefs.set("pwa", it) }
                FeatureRow(
                    Icons.Rounded.Speed, stringResource(R.string.f_rate), stringResource(R.string.f_rate_sub), s.rateOn, divider = true,
                    extra = {
                        val opts = listOf(0, 2, 5, 10, 20, 50).map { it.toString() to if (it == 0) "∞" else it.toString() }
                        Text(stringResource(R.string.rate_local), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Segmented(opts, s.rateLocal.toString()) { v -> Prefs.update { putInt("rateLocal", v.toInt()) } }
                        Text(stringResource(R.string.rate_remote), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Segmented(opts, s.rateRemote.toString()) { v -> Prefs.update { putInt("rateRemote", v.toInt()) } }
                        Text(stringResource(R.string.rate_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                ) { Prefs.set("rateOn", it) }
                // WEBFEATURES
            }
        }

        item { SectionTitle(stringResource(R.string.sec_remote)) }
        item {
            ExpressiveCard(color = MaterialTheme.colorScheme.errorContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.remote_warn_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.remote_warn_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        item {
            ExpressiveCard {
                if (!Tunnel.supported) {
                    Text(stringResource(R.string.remote_unsupported), style = MaterialTheme.typography.bodyMedium)
                } else {
                    FeatureRow(
                        Icons.Rounded.Public, stringResource(R.string.f_remote), stringResource(R.string.f_remote_sub), s.remoteOn,
                        extra = {
                            val stateText = when (tunnelState) {
                                Tunnel.State.OFF -> stringResource(R.string.remote_state_off)
                                Tunnel.State.STARTING -> stringResource(R.string.remote_state_starting)
                                Tunnel.State.UP -> stringResource(R.string.remote_state_up)
                                Tunnel.State.ERROR -> stringResource(R.string.remote_state_error, tunnelError.orEmpty())
                            }
                            Text(stateText, style = MaterialTheme.typography.bodyMedium,
                                color = if (tunnelState == Tunnel.State.ERROR) MaterialTheme.colorScheme.error else if (tunnelState == Tunnel.State.UP) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            tunnelUrl?.let { u ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(u, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    TextButton(onClick = {
                                        ctx.getSystemService(android.content.ClipboardManager::class.java)
                                            .setPrimaryClip(android.content.ClipData.newPlainText("Akış", u))
                                    }) { Text(stringResource(R.string.copy)) }
                                }
                            }
                            Segmented(
                                options = listOf("quick" to stringResource(R.string.remote_mode_quick), "token" to stringResource(R.string.remote_mode_token)),
                                selected = s.remoteMode,
                            ) { v -> Prefs.update { putString("remoteMode", v) } }
                            if (s.remoteMode == "token") {
                                Text(stringResource(R.string.remote_token_note, s.port), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedTextField(
                                    value = tokenDraft, onValueChange = { tokenDraft = it.trim() }, singleLine = true,
                                    label = { Text(stringResource(R.string.remote_token)) }, visualTransformation = PasswordVisualTransformation(),
                                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = hostDraft, onValueChange = { hostDraft = it.trim() }, singleLine = true,
                                    label = { Text(stringResource(R.string.remote_host)) },
                                    shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                                )
                                if (tokenDraft != s.remoteToken || hostDraft != s.remoteHost) {
                                    Button(onClick = { Prefs.update { putString("remoteToken", tokenDraft); putString("remoteHost", hostDraft) } }) { Text(stringResource(R.string.save)) }
                                }
                            } else {
                                Text(stringResource(R.string.remote_quick_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            Text(stringResource(R.string.remote_sessions, remoteSessions), style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { editRemotePw = true }) { Text(stringResource(R.string.remote_pw_change)) }
                                if (remoteSessions > 0) TextButton(onClick = { RemoteAuth.endAll(); remoteSessions = 0 }) { Text(stringResource(R.string.remote_end_all)) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.remote_approve), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                Switch(s.remoteApprove, { v -> Prefs.set("remoteApprove", v) })
                            }
                            if (s.totpSecret.isEmpty()) Text(stringResource(R.string.remote_totp_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.remote_allow), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf(
                                Triple("remoteUpload", stringResource(R.string.f_upload), s.remoteUpload),
                                Triple("remoteCamera", stringResource(R.string.f_camera), s.remoteCamera),
                                Triple("remoteStatus", stringResource(R.string.f_status), s.remoteStatus),
                                Triple("remoteApps", stringResource(R.string.f_apps), s.remoteApps),
                                Triple("remoteScreen", stringResource(R.string.f_screen_short), s.remoteScreen),
                                Triple("remoteNotifs", stringResource(R.string.f_notifs_short), s.remoteNotifs),
                                Triple("remoteInbox", stringResource(R.string.f_inbox_short), s.remoteInbox),
                                Triple("remoteMic", stringResource(R.string.f_mic_short), s.remoteMic),
                            ).forEach { (key, label, on) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(on, { v -> Prefs.set(key, v) })
                                }
                            }
                        },
                    ) { on ->
                        when {
                            !on -> Prefs.set("remoteOn", false)
                            !RemoteAuth.hasPassword -> { remoteAfterPw = true; editRemotePw = true }
                            !s.remoteWarned -> remoteConfirm = true
                            else -> Prefs.set("remoteOn", true)
                        }
                    }
                }
            }
        }

        val blocked = devices.filter { !it.allowed }
        if (blocked.isNotEmpty() && !s.deviceApproval) {
            item { SectionTitle(stringResource(R.string.blocked_devices)) }
            item {
                ExpressiveCard {
                    blocked.forEach { d ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    d.ip + " • " + if (d.until > 0) stringResource(R.string.device_banned_until, untilText(d.until)) else stringResource(R.string.device_blocked),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            TextButton(onClick = { DeviceAccess.forget(d.ip) }) { Text(stringResource(R.string.unblock)) }
                        }
                    }
                }
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
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { Prefs.set("onboarded", false) }) {
                    Icon(Icons.Rounded.School, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ob_replay))
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
    if (editAdminPw) {
        var pw1 by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        var errText by remember { mutableStateOf<String?>(null) }
        val mismatch = stringResource(R.string.admin_pw_mismatch)
        val minMsg = stringResource(R.string.admin_pw_dialog)
        AlertDialog(
            onDismissRequest = { editAdminPw = false },
            title = { Text(stringResource(R.string.admin_pw_dialog)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pw1, onValueChange = { pw1 = it; errText = null }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it; errText = null }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.admin_pw_repeat)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    )
                    errText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pw1.length < 6 -> errText = minMsg
                        pw1 != pw2 -> errText = mismatch
                        else -> {
                            Admin.setPassword(pw1)
                            Prefs.set("adminOn", true)
                            editAdminPw = false
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editAdminPw = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    totpSetup?.let { secret ->
        var code by remember { mutableStateOf("") }
        var wrong by remember { mutableStateOf(false) }
        val keyText = secret.chunked(4).joinToString(" ")
        AlertDialog(
            onDismissRequest = { totpSetup = null },
            title = { Text(stringResource(R.string.totp_setup)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.totp_steps), style = MaterialTheme.typography.bodySmall)
                    Box(Modifier.background(Color.White, MaterialTheme.shapes.medium).padding(10.dp)) {
                        androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme()) {
                            DotQrCode(Totp.uri(secret, s.name), Modifier.size(180.dp))
                        }
                    }
                    Text(stringResource(R.string.totp_key, keyText), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(6); wrong = false }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.totp_code)) }, isError = wrong,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    )
                    if (wrong) Text(stringResource(R.string.totp_wrong), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Totp.check(code, secret)) {
                        Prefs.update { putString("totpSecret", secret) }
                        totpSetup = null
                    } else wrong = true
                }) { Text(stringResource(R.string.totp_verify)) }
            },
            dismissButton = { TextButton(onClick = { totpSetup = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (remoteConfirm) {
        AlertDialog(
            onDismissRequest = { remoteConfirm = false },
            icon = { Icon(Icons.Rounded.Warning, null) },
            title = { Text(stringResource(R.string.remote_warn_title)) },
            text = { Text(stringResource(R.string.remote_warn_text)) },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.update { putBoolean("remoteWarned", true); putBoolean("remoteOn", true) }
                    remoteConfirm = false
                }) { Text(stringResource(R.string.remote_confirm)) }
            },
            dismissButton = { TextButton(onClick = { remoteConfirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editRemotePw) {
        var pw1 by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        var errText by remember { mutableStateOf<String?>(null) }
        val mismatch = stringResource(R.string.admin_pw_mismatch)
        val minMsg = stringResource(R.string.remote_pw_dialog)
        AlertDialog(
            onDismissRequest = { editRemotePw = false; remoteAfterPw = false },
            title = { Text(stringResource(R.string.remote_pw_dialog)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pw1, onValueChange = { pw1 = it; errText = null }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it; errText = null }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.admin_pw_repeat)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                    )
                    errText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        pw1.length < 8 -> errText = minMsg
                        pw1 != pw2 -> errText = mismatch
                        else -> {
                            RemoteAuth.setPassword(pw1)
                            remoteSessions = 0
                            editRemotePw = false
                            if (remoteAfterPw) {
                                remoteAfterPw = false
                                if (s.remoteWarned) Prefs.set("remoteOn", true) else remoteConfirm = true
                            }
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { editRemotePw = false; remoteAfterPw = false }) { Text(stringResource(R.string.cancel)) } },
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
private fun untilText(until: Long): String {
    val ctx = LocalContext.current
    val sameDay = until - System.currentTimeMillis() < 20 * 60 * 60 * 1000L
    return android.text.format.DateUtils.formatDateTime(
        ctx, until,
        android.text.format.DateUtils.FORMAT_SHOW_TIME or (if (sameDay) 0 else android.text.format.DateUtils.FORMAT_SHOW_DATE),
    )
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
