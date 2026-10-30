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
    val shares by QuickShare.shares.collectAsStateWithLifecycle()
    var qrFor by remember { mutableStateOf<String?>(null) }

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
                    AddressCard(addr, settings.webUi, onQr = { showQr = true }, modifier = Modifier.animateItem())
                }
            }
            item(key = "stats") {
                Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatPill(Icons.Rounded.Devices, clients.size.toString(), stringResource(R.string.stat_devices), Modifier.weight(1f))
                    StatPill(Icons.Rounded.Upload, formatBytes(bytes), stringResource(R.string.stat_sent), Modifier.weight(1f))
                    StatPill(Icons.Rounded.Schedule, formatDuration((now - startedAt).coerceAtLeast(0)), stringResource(R.string.stat_uptime), Modifier.weight(1f))
                }
            }
            if (streams.isNotEmpty()) {
                item(key = "st-title") { SectionTitle(stringResource(R.string.now_playing), Modifier.animateItem()) }
                items(streams, key = { "s-" + it.key }) { StreamCard(it, Modifier.animateItem()) }
            }
            if (shares.isNotEmpty()) {
                item(key = "sh-title") { SectionTitle(stringResource(R.string.active_shares), Modifier.animateItem()) }
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
        val url = QuickShare.url(tk)
        AlertDialog(
            onDismissRequest = { qrFor = null },
            confirmButton = { TextButton(onClick = { qrFor = null }) { Text(stringResource(R.string.close)) } },
            title = { Text(stringResource(R.string.share_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (url != null) {
                        DotQrCode(url, Modifier.size(220.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(url, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(R.string.waiting_network))
                    }
                }
            },
        )
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
                    DotQrCode(address!!, Modifier.size(240.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(address!!, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
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
private fun AddressCard(address: String, webUi: Boolean, onQr: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    ExpressiveCard(modifier = modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Wifi, bg = MaterialTheme.colorScheme.primary, tint = MaterialTheme.colorScheme.onPrimary)
            Gap(14.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.network_address), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                Text(address.removePrefix("http://"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            FilledTonalIconButton(onClick = { clipboard.setText(AnnotatedString(address)) }) {
                Icon(Icons.Rounded.ContentCopy, stringResource(R.string.copy))
            }
            if (webUi) {
                FilledTonalIconButton(onClick = onQr) { Icon(Icons.Rounded.QrCode2, stringResource(R.string.qr_code)) }
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
