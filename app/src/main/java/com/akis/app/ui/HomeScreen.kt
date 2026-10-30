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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.ClientInfo
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
                        if (on) "Yayında" else "Kapalı",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (on) "Ağdaki cihazlar seni görebiliyor" else "Başlatmak için düğmeye dokun",
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
                    StatPill(Icons.Rounded.Devices, clients.size.toString(), "Cihaz", Modifier.weight(1f))
                    StatPill(Icons.Rounded.Upload, formatBytes(bytes), "Gönderilen", Modifier.weight(1f))
                    StatPill(Icons.Rounded.Schedule, formatDuration((now - startedAt).coerceAtLeast(0)), "Süre", Modifier.weight(1f))
                }
            }
            if (streams.isNotEmpty()) {
                item(key = "st-title") { SectionTitle("Şu an oynatılıyor", Modifier.animateItem()) }
                items(streams, key = { "s-" + it.key }) { StreamCard(it, Modifier.animateItem()) }
            }
            item(key = "cl-title") { SectionTitle("Bağlı cihazlar", Modifier.animateItem()) }
            if (clients.isEmpty()) {
                item(key = "cl-empty") {
                    ExpressiveCard(modifier = Modifier.animateItem()) {
                        Text("Henüz kimse bağlanmadı", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "VLC'de Yerel Ağ → Universal Plug'n'Play bölümünü aç. \"${settings.name}\" listede görünecek.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(clients, key = { "c-" + it.ip }) { ClientRow(it, now, Modifier.animateItem()) }
            }
            if (events.isNotEmpty()) {
                item(key = "ev-title") { SectionTitle("Etkinlik", Modifier.animateItem()) }
                item(key = "ev") { EventsCard(events.take(8).map { it.time to it.text }, Modifier.animateItem()) }
            }
        } else {
            item(key = "howto") { HowToCard(Modifier.animateItem()) }
        }
    }

    if (showQr && address != null) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Kapat") } },
            title = { Text("Tarayıcıdan aç") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    DotQrCode(address!!, Modifier.size(240.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(address!!, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Aynı ağdaki telefon, PC ya da TV tarayıcısıyla okut.",
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
                Text("Ağ adresi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                Text(address.removePrefix("http://"), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            FilledTonalIconButton(onClick = { clipboard.setText(AnnotatedString(address)) }) {
                Icon(Icons.Rounded.ContentCopy, "Kopyala")
            }
            if (webUi) {
                FilledTonalIconButton(onClick = onQr) { Icon(Icons.Rounded.QrCode2, "QR kod") }
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
private fun ClientRow(c: ClientInfo, now: Long, modifier: Modifier = Modifier) {
    val icon = when {
        c.name.contains("TV") -> Icons.Rounded.Tv
        c.name == "VLC" || c.name == "Kodi" -> Icons.Rounded.PlayArrow
        c.name == "Windows" -> Icons.Rounded.Computer
        c.name == "Tarayıcı" -> Icons.Rounded.Language
        c.name == "Xbox" || c.name == "PlayStation" -> Icons.Rounded.SportsEsports
        else -> Icons.Rounded.Devices
    }
    val ago = ((now - c.lastSeen) / 1000).coerceAtLeast(0)
    val agoText = when {
        ago < 10 -> "şimdi"
        ago < 60 -> "$ago sn önce"
        else -> "${ago / 60} dk önce"
    }
    ExpressiveCard(modifier = modifier) {
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
        Text("Nasıl kullanılır?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        listOf(
            "Telefonla diğer cihazı aynı Wi-Fi ağına bağla (telefonun hotspot'u da olur).",
            "Yukarıdaki düğmeye dokunup yayını başlat.",
            "VLC'de Yerel Ağ → Universal Plug'n'Play bölümüne gir; telefonun orada görünür.",
            "TV ya da PC tarayıcısından adrese girerek de izleyebilirsin.",
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
