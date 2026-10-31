package com.akis.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.akis.app.R
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.ClientInfo
import com.akis.app.DeviceAccess
import com.akis.app.QuickShare
import com.akis.app.FileRequests
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Timer
import com.akis.app.Prefs
import com.akis.app.ServerState
import com.akis.app.StreamInfo
import com.akis.app.dlna.Kind
import com.akis.app.service.DlnaService
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val running by ServerState.running.collectAsStateWithLifecycle()
    val address by ServerState.address.collectAsStateWithLifecycle()
    val clients by ServerState.clients.collectAsStateWithLifecycle()
    val streams by ServerState.streams.collectAsStateWithLifecycle()
    val events by ServerState.events.collectAsStateWithLifecycle()
    val bytes by ServerState.bytesSent.collectAsStateWithLifecycle()
    val startedAt by ServerState.startedAt.collectAsStateWithLifecycle()
    val error by ServerState.error.collectAsStateWithLifecycle()
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    var showQr by remember { mutableStateOf(false) }
    var actFor by remember { mutableStateOf<ClientInfo?>(null) }
    val tunnelState by com.akis.app.Tunnel.state.collectAsStateWithLifecycle()
    val remoteUrl by com.akis.app.Tunnel.url.collectAsStateWithLifecycle()
    var qrTab by remember { mutableStateOf("local") }
    val shares by QuickShare.shares.collectAsStateWithLifecycle()
    var qrFor by remember { mutableStateOf<String?>(null) }
    val requests by FileRequests.list.collectAsStateWithLifecycle()
    var newRequest by remember { mutableStateOf(false) }
    var qrRequest by remember { mutableStateOf<String?>(null) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "header") { Header(running, settings.name) }
        item(key = "power") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PowerButton(running) { if (running) DlnaService.stop(ctx) else DlnaService.start(ctx) }
            }
        }
        item(key = "status") {
            AnimatedContent(
                targetState = running,
                transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { -it / 2 }) },
                label = "status",
                modifier = Modifier.fillMaxWidth(),
            ) { on ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(if (on) R.string.status_on else R.string.status_off),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(if (on) R.string.status_on_sub else R.string.status_off_sub),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        error?.let { msg ->
            item(key = "error") {
                ExpressiveCard(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Gap()
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (running) {
            address?.let { addr ->
                item(key = "addr") {
                    AddressCard(addr, remoteUrl.takeIf { tunnelState == com.akis.app.Tunnel.State.UP }, settings.webUi, onQr = { showQr = true }, modifier = Modifier.animateItem())
                }
            }
            item(key = "stats") {
                Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatPill(Icons.Rounded.Devices, clients.size.toString(), stringResource(R.string.stat_devices), Modifier.weight(1f))
                    StatPill(Icons.Rounded.Upload, formatBytes(bytes), stringResource(R.string.stat_sent), Modifier.weight(1f))
                    StatPill(Icons.Rounded.Schedule, formatDuration((now - startedAt).coerceAtLeast(0)), stringResource(R.string.stat_uptime), Modifier.weight(1f))
                }
            }
            if (settings.fileReq) {
                item(key = "req-new") {
                    ExpressiveCard(modifier = Modifier.animateItem(), onClick = { newRequest = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.MoveToInbox, size = 44.dp)
                            Gap(14.dp)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.req_new), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.req_new_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            if (streams.isNotEmpty()) {
                item(key = "st-title") { SectionTitle(stringResource(R.string.now_playing), Modifier.animateItem()) }
                items(streams, key = { "s-" + it.key }) { StreamCard(it, Modifier.animateItem()) }
            }
            if (shares.isNotEmpty() || requests.isNotEmpty()) {
                item(key = "sh-title") { SectionTitle(stringResource(R.string.active_shares), Modifier.animateItem()) }
                items(requests, key = { "rq-" + it.token }) { rq ->
                    ExpressiveCard(modifier = Modifier.animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.MoveToInbox, size = 44.dp)
                            Gap(14.dp)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.req_item), style = MaterialTheme.typography.titleMedium)
                                val left = formatDuration((rq.expires - now).coerceAtLeast(0))
                                val extra = plural(R.plurals.req_files_n, rq.files) + (if (rq.bytes > 0) " • " + formatBytes(rq.bytes) else "")
                                Text(stringResource(R.string.share_left, left, extra), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalIconButton(onClick = { qrRequest = rq.token }) { Icon(Icons.Rounded.QrCode2, stringResource(R.string.show_qr)) }
                            FilledTonalIconButton(onClick = { FileRequests.remove(rq.token) }) { Icon(Icons.Rounded.LinkOff, stringResource(R.string.req_stop)) }
                        }
                    }
                }
                items(shares, key = { "sh-" + it.token }) { sh ->
                    ExpressiveCard(modifier = Modifier.animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Link, size = 44.dp)
                            Gap(14.dp)
                            Column(Modifier.weight(1f)) {
                                Text(sh.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val left = formatDuration((sh.expires - now).coerceAtLeast(0))
                                val extra = if (sh.once) stringResource(R.string.share_once_tag) else stringResource(R.string.share_downloads, sh.downloads)
                                Text(stringResource(R.string.share_left, left, extra), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            FilledTonalIconButton(onClick = { qrFor = sh.token }) { Icon(Icons.Rounded.QrCode2, stringResource(R.string.show_qr)) }
                            FilledTonalIconButton(onClick = { QuickShare.remove(sh.token) }) { Icon(Icons.Rounded.LinkOff, stringResource(R.string.share_stop)) }
                        }
                    }
                }
            }
            item(key = "cl-title") { SectionTitle(stringResource(R.string.connected_devices), Modifier.animateItem()) }
            if (clients.isEmpty()) {
                item(key = "cl-empty") {
                    ExpressiveCard(modifier = Modifier.animateItem()) {
                        Text(stringResource(R.string.nobody_yet), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.nobody_hint, settings.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(clients, key = { "c-" + it.ip }) { ClientRow(it, now, Modifier.animateItem()) { actFor = it } }
            }
            if (events.isNotEmpty()) {
                item(key = "ev-title") { SectionTitle(stringResource(R.string.activity), Modifier.animateItem()) }
                item(key = "ev") { EventsCard(events.take(8).map { it.time to it.text }, Modifier.animateItem()) }
            }
        } else {
            item(key = "howto") { HowToCard(Modifier.animateItem()) }
        }
    }

    qrFor?.let { tk ->
        val up = remoteUrl != null && tunnelState == com.akis.app.Tunnel.State.UP
        LinkQrDialog(stringResource(R.string.share_title), null, { r -> if (r) remoteUrl?.let { "${it.trimEnd('/')}/share/$tk" } else QuickShare.url(tk) }, up) { qrFor = null }
    }
    if (newRequest) {
        var minutes by remember { mutableStateOf(60) }
        var limit by remember { mutableStateOf(0L) }
        var once by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { newRequest = false },
            title = { Text(stringResource(R.string.req_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.req_duration), style = MaterialTheme.typography.labelLarge)
                    Choice(listOf(15 to stringResource(R.string.req_15m), 60 to stringResource(R.string.req_1h), 24 * 60 to stringResource(R.string.req_1d)), minutes) { minutes = it }
                    Text(stringResource(R.string.req_limit), style = MaterialTheme.typography.labelLarge)
                    Choice(listOf(0L to stringResource(R.string.req_nolimit), 100L shl 20 to "100 MB", 1L shl 30 to "1 GB", 10L shl 30 to "10 GB"), limit) { limit = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.req_once), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        androidx.compose.material3.Switch(once, { once = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    qrRequest = FileRequests.create(minutes, limit, once).token
                    newRequest = false
                }) { Text(stringResource(R.string.req_create)) }
            },
            dismissButton = { TextButton(onClick = { newRequest = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    qrRequest?.let { tk ->
        LinkQrDialog(stringResource(R.string.req_new), stringResource(R.string.req_hint), { FileRequests.url(tk, it) }, remoteUrl != null && tunnelState == com.akis.app.Tunnel.State.UP) { qrRequest = null }
    }
    actFor?.let { c ->
        AlertDialog(
            onDismissRequest = { actFor = null },
            title = { Text(c.name) },
            text = {
                Column {
                    Text(c.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    DeviceAction(Icons.Rounded.LinkOff, stringResource(R.string.kick), stringResource(R.string.kick_sub)) { DeviceAccess.kick(c.ip, c.name); actFor = null }
                    DeviceAction(Icons.Rounded.Timer, stringResource(R.string.ban_15), stringResource(R.string.ban_sub)) { DeviceAccess.ban(c.ip, c.name, 15); actFor = null }
                    DeviceAction(Icons.Rounded.Timer, stringResource(R.string.ban_60), null) { DeviceAccess.ban(c.ip, c.name, 60); actFor = null }
                    DeviceAction(Icons.Rounded.Timer, stringResource(R.string.ban_1d), null) { DeviceAccess.ban(c.ip, c.name, 24 * 60); actFor = null }
                    DeviceAction(Icons.Rounded.Block, stringResource(R.string.block_forever), stringResource(R.string.block_sub), danger = true) { DeviceAccess.block(c.ip, c.name); actFor = null }
                }
            },
            confirmButton = { TextButton(onClick = { actFor = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showQr && address != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text(stringResource(R.string.close)) } },
            title = { Text(stringResource(R.string.open_in_browser)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    val remoteUp = remoteUrl != null && tunnelState == com.akis.app.Tunnel.State.UP
                    val tabs = buildList {
                        add("local" to stringResource(R.string.qr_local))
                        if (remoteUp) add("remote" to stringResource(R.string.qr_remote))
                        if (settings.wifiQr) add("wifi" to stringResource(R.string.qr_wifi))
                    }
                    val tab = qrTab.takeIf { t -> tabs.any { it.first == t } } ?: "local"
                    if (tabs.size > 1) {
                        Choice(tabs, tab) { qrTab = it }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (tab == "wifi") {
                        WifiQr(settings.wifiSsid, settings.wifiPass, address!!)
                    } else {
                        val target = if (tab == "remote") remoteUrl!! else address!!
                        DotQrCode(target, Modifier.size(240.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(target, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.qr_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}

/** Ağa bağlanma QR'ı (WIFI:… biçimi; telefon kameraları tanır) ve ardından açılacak adres. */
@Composable
private fun WifiQr(ssid: String, pass: String, address: String) {
    var editing by remember { mutableStateOf(ssid.isEmpty()) }
    if (editing) {
        var s by remember { mutableStateOf(ssid) }
        var p by remember { mutableStateOf(pass) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = s, onValueChange = { s = it }, singleLine = true, label = { Text(stringResource(R.string.wifi_ssid)) },
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.OutlinedTextField(
                value = p, onValueChange = { p = it }, singleLine = true, label = { Text(stringResource(R.string.wifi_pass)) },
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.wifi_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.Button(
                enabled = s.isNotBlank(),
                onClick = { Prefs.update { putString("wifiSsid", s.trim()); putString("wifiPass", p) }; editing = false },
            ) { Text(stringResource(R.string.save)) }
        }
        return
    }
    fun e(v: String) = v.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")
    val code = if (pass.isEmpty()) "WIFI:T:nopass;S:${e(ssid)};;" else "WIFI:T:WPA;S:${e(ssid)};P:${e(pass)};;"
    DotQrCode(code, Modifier.size(240.dp))
    Spacer(Modifier.height(10.dp))
    Text(ssid, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.wifi_then), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(address, style = MaterialTheme.typography.titleMedium)
    TextButton(onClick = { editing = true }) { Text(stringResource(R.string.wifi_edit)) }
}

/** Seçenek düğmeleri (tek seçim). */
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (v, label) ->
            androidx.compose.material3.SegmentedButton(
                selected = v == selected, onClick = { onSelect(v) },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label, maxLines = 1) }
        }
    }
}

/** Bağlantı + QR; uzaktan erişim açıksa Yerel / Uzak seçilebilir. */
@Composable
private fun LinkQrDialog(title: String, hint: String?, url: (remote: Boolean) -> String?, remoteUp: Boolean, onDismiss: () -> Unit) {
    var remote by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (remoteUp) {
                    Choice(listOf(false to stringResource(R.string.qr_local), true to stringResource(R.string.qr_remote)), remote) { remote = it }
                    Spacer(Modifier.height(12.dp))
                }
                val u = url(remoteUp && remote)
                if (u != null) {
                    DotQrCode(u, Modifier.size(220.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        FilledTonalIconButton(onClick = { clipboard.setText(AnnotatedString(u)) }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.copy)) }
                    }
                    if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(R.string.waiting_network))
                }
            }
        },
    )
}

@Composable
private fun Header(running: Boolean, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) {
        LiveDot(running, 14.dp)
        Gap(12.dp)
        Column {
            Text("Akış", style = MaterialTheme.typography.headlineLarge)
            Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun AddressCard(address: String, remote: String?, webUi: Boolean, onQr: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    ExpressiveCard(modifier = modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Wifi, bg = MaterialTheme.colorScheme.primary, tint = MaterialTheme.colorScheme.onPrimary)
            Gap(14.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (remote != null) R.string.local_address else R.string.network_address),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
                Text(address.removePrefix("http://"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            FilledTonalIconButton(onClick = { clipboard.setText(AnnotatedString(address)) }) {
                Icon(Icons.Rounded.ContentCopy, stringResource(R.string.copy))
            }
            if (webUi) {
                FilledTonalIconButton(onClick = onQr) { Icon(Icons.Rounded.QrCode2, stringResource(R.string.qr_code)) }
            }
        }
        if (remote != null) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Public, bg = MaterialTheme.colorScheme.tertiary, tint = MaterialTheme.colorScheme.onTertiary)
                Gap(14.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remote_address), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    Text(remote.removePrefix("https://"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2)
                }
                FilledTonalIconButton(onClick = { clipboard.setText(AnnotatedString(remote)) }) {
                    Icon(Icons.Rounded.ContentCopy, stringResource(R.string.copy))
                }
            }
        }
    }
}

@Composable
private fun StreamCard(s: StreamInfo, modifier: Modifier = Modifier) {
    ExpressiveCard(modifier = modifier, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                if (s.kind == Kind.AUDIO) Icons.Rounded.MusicNote else Icons.Rounded.Movie,
                bg = MaterialTheme.colorScheme.primary, tint = MaterialTheme.colorScheme.onPrimary,
            )
            Gap(14.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold,
                )
                Text("${s.client} • ${s.ip}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
            }
            Icon(Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(12.dp))
        val progress = if (s.size > 0) (s.position.toFloat() / s.size).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${formatBytes(s.position)} / ${formatBytes(s.size)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun DeviceAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color)
            Gap(14.dp)
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ClientRow(c: ClientInfo, now: Long, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val icon = when {
        c.name.contains("TV") -> Icons.Rounded.Tv
        c.name == "VLC" || c.name == "Kodi" -> Icons.Rounded.PlayArrow
        c.name == "Windows" -> Icons.Rounded.Computer
        c.name == stringResource(R.string.client_browser) -> Icons.Rounded.Language
        c.name == "Xbox" || c.name == "PlayStation" -> Icons.Rounded.SportsEsports
        else -> Icons.Rounded.Devices
    }
    val ago = ((now - c.lastSeen) / 1000).coerceAtLeast(0)
    val agoText = when {
        ago < 10 -> stringResource(R.string.ago_now)
        ago < 60 -> stringResource(R.string.ago_sec, ago.toInt())
        else -> stringResource(R.string.ago_min, (ago / 60).toInt())
    }
    ExpressiveCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, bg = MaterialTheme.colorScheme.tertiaryContainer, tint = MaterialTheme.colorScheme.onTertiaryContainer, size = 44.dp)
            Gap(14.dp)
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(c.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(agoText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EventsCard(events: List<Pair<Long, String>>, modifier: Modifier = Modifier) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    ExpressiveCard(modifier = modifier) {
        events.forEach { (t, text) ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(fmt.format(Date(t)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Gap(12.dp)
                Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HowToCard(modifier: Modifier = Modifier) {
    ExpressiveCard(modifier = modifier) {
        Text(stringResource(R.string.howto_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        listOf(
            stringResource(R.string.howto_1),
            stringResource(R.string.howto_2),
            stringResource(R.string.howto_3),
            stringResource(R.string.howto_4),
        ).forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Gap(12.dp)
                Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
