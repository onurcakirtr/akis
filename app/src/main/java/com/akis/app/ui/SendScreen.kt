package com.akis.app.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.Prefs
import com.akis.app.WatchHistory
import com.akis.app.cast.Remote
import com.akis.app.cast.RemoteState
import com.akis.app.cast.TvDevice
import com.akis.app.cast.TvType
import com.akis.app.dlna.Entry
import com.akis.app.dlna.Kind
import com.akis.app.dlna.MediaLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/** "Paylaş → Akış" ile gelen ve TV seçilince oynatılacak dosya. */
val pendingShare = MutableStateFlow<Entry?>(null)

@Composable
fun SendScreen() {
    val ctx = LocalContext.current
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    val state by Remote.state.collectAsStateWithLifecycle()
    val devices by Remote.devices.collectAsStateWithLifecycle()
    val message by Remote.message.collectAsStateWithLifecycle()
    val share by pendingShare.collectAsStateWithLifecycle()
    val history by WatchHistory.records.collectAsStateWithLifecycle()
    val lib = remember { MediaLibrary(ctx.applicationContext) }

    var stack by remember { mutableStateOf(listOf("0" to "Kitaplık")) }
    var query by remember { mutableStateOf("") }
    var scanTick by remember { mutableStateOf(0) }

    DisposableEffect(settings.cast, scanTick) {
        Remote.startScan()
        onDispose { Remote.stopScan() }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            Remote.message.value = null
        }
    }
    // Paylaşılan dosya: TV seçilince kendiliğinden oynat
    LaunchedEffect(share, state.connected) {
        val e = share
        if (e != null && state.connected) {
            Remote.play(e)
            pendingShare.value = null
        }
    }
    BackHandler(enabled = stack.size > 1 || query.isNotEmpty()) {
        if (query.isNotEmpty()) {
            query = ""
        } else {
            stack = stack.dropLast(1)
        }
    }

    val folderId = stack.last().first
    val entries by produceState<List<Entry>?>(null, folderId, query, settings) {
        value = null
        value = withContext(Dispatchers.IO) {
            if (query.isNotBlank()) lib.search(query, limit = 150) else lib.children(folderId)
        }
    }
    val progress = remember(history) { history.associate { it.id to it } }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text("Gönder", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp))
            Text(
                "Telefondakileri TV'de oynat, buradan kumanda et",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }

        if (!settings.cast) {
            item(key = "off") {
                ExpressiveCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Cast, bg = MaterialTheme.colorScheme.surfaceContainerHighest, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Gap(14.dp)
                        Column(Modifier.weight(1f)) {
                            Text("TV'ye gönderme kapalı", style = MaterialTheme.typography.titleMedium)
                            Text("Ayarlar → Özellikler'den açabilirsin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { Prefs.set("cast", true); Remote.ensureInit() }) { Text("Şimdi aç") }
                }
            }
            return@LazyColumn
        }

        message?.let { msg ->
            item(key = "msg") {
                ExpressiveCard(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Gap()
                        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
        }

        share?.let { e ->
            item(key = "share") {
                ExpressiveCard(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.animateItem()) {
                    Text("Gönderilecek", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                    Text(e.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 2)
                    Text("Aşağıdan bir TV seç; seçince oynatılır.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        if (state.hasMedia) {
            item(key = "remote") { RemoteCard(state, Modifier.animateItem()) }
        }

        item(key = "tv-title") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem()) {
                SectionTitle("TV'ler", Modifier.weight(1f))
                IconButton(onClick = { scanTick++ }) { Icon(Icons.Rounded.Refresh, "Yeniden ara") }
            }
        }
        if (devices.isEmpty() && state.device == null) {
            item(key = "tv-empty") {
                ExpressiveCard(modifier = Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Gap(14.dp)
                        Column {
                            Text("TV aranıyor…", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Google TV, Chromecast ve DLNA TV'ler aynı Wi-Fi ağındaysa burada görünür.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        val shown = (listOfNotNull(state.device) + devices.filter { it.id != state.device?.id && it.name != state.device?.name })
        items(shown, key = { "tv-" + it.type + it.id }) { d ->
            DeviceRow(d, connected = state.device?.id == d.id || state.device?.name == d.name, connecting = state.connecting == d.name, Modifier.animateItem())
        }

        item(key = "lib-title") { SectionTitle("Kitaplık", Modifier.animateItem()) }
        if (settings.search) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Ara…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (query.isBlank() && stack.size > 1) {
            item(key = "crumb-" + folderId) {
                Surface(
                    onClick = { stack = stack.dropLast(1) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.animateItem(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                        Gap(10.dp)
                        Text(stack.last().second, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val list = entries
        when {
            list == null -> item(key = "lib-loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            list.isEmpty() -> item(key = "lib-empty") {
                Text(
                    if (query.isNotBlank()) "Sonuç yok" else "Burada bir şey yok",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            else -> items(list, key = { "e-" + it.id + "-" + it.parentId }) { e ->
                EntryRow(
                    e, lib,
                    playingNow = state.entryId == e.id,
                    progress = progress[e.id]?.takeIf { settings.history && it.inProgress }?.fraction,
                    watched = settings.history && progress[e.id]?.watched == true,
                    modifier = Modifier.animateItem(),
                ) {
                    if (e.kind == Kind.FOLDER) {
                        query = ""
                        stack = stack + (e.id to e.title)
                    } else if (!state.connected) {
                        Remote.message.value = "Önce yukarıdan bir TV seç"
                    } else {
                        Remote.play(e)
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteCard(st: RemoteState, modifier: Modifier = Modifier) {
    val on = MaterialTheme.colorScheme.onPrimaryContainer
    var dragging by remember { mutableStateOf<Float?>(null) }
    ExpressiveCard(modifier = modifier, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                when (st.kind) { Kind.AUDIO -> Icons.Rounded.MusicNote; Kind.IMAGE -> Icons.Rounded.Image; else -> Icons.Rounded.Movie },
                bg = MaterialTheme.colorScheme.primary, tint = MaterialTheme.colorScheme.onPrimary,
            )
            Gap(14.dp)
            Column(Modifier.weight(1f)) {
                Text(st.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = on, fontWeight = FontWeight.SemiBold)
                Text(
                    (st.device?.name ?: "") + if (st.buffering) " • yükleniyor…" else "",
                    style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.75f),
                )
            }
        }
        if (st.kind != Kind.IMAGE) {
            Spacer(Modifier.height(10.dp))
            val frac = if (st.durationMs > 0) (st.positionMs.toFloat() / st.durationMs).coerceIn(0f, 1f) else 0f
            Slider(
                value = dragging ?: frac,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { Remote.seek((it * st.durationMs).toLong()) }
                    dragging = null
                },
                enabled = st.durationMs > 0,
                colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = on.copy(alpha = 0.15f)),
            )
            Row {
                val shownPos = dragging?.let { (it * st.durationMs).toLong() } ?: st.positionMs
                Text(formatDuration(shownPos), style = MaterialTheme.typography.labelSmall, color = on.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                Text(formatDuration(st.durationMs), style = MaterialTheme.typography.labelSmall, color = on.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { Remote.skip(-10_000) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Replay10, "10 sn geri") }
                FilledIconButton(
                    onClick = { Remote.togglePlay() },
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(percent = if (st.playing) 30 else 50),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(if (st.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (st.playing) "Duraklat" else "Oynat", Modifier.size(36.dp))
                }
                FilledTonalIconButton(onClick = { Remote.skip(30_000) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Forward30, "30 sn ileri") }
                FilledTonalIconButton(onClick = { Remote.stop() }, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.Stop, "Durdur") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { Remote.toggleMute() }) {
                Icon(if (st.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Ses", tint = on)
            }
            Slider(
                value = st.volume,
                onValueChange = { Remote.setVolume(it) },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = on.copy(alpha = 0.15f)),
            )
        }
    }
}

@Composable
private fun DeviceRow(d: TvDevice, connected: Boolean, connecting: Boolean, modifier: Modifier = Modifier) {
    ExpressiveCard(
        modifier = modifier,
        color = if (connected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        onClick = { if (!connected) Remote.connect(d) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                when {
                    connected -> Icons.Rounded.CastConnected
                    d.type == TvType.DLNA && !d.description.contains("TV", true) -> Icons.Rounded.Speaker
                    else -> Icons.Rounded.Tv
                },
                bg = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiaryContainer,
                tint = if (connected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiaryContainer,
                size = 44.dp,
            )
            Gap(14.dp)
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        connected -> "Bağlı • " + if (d.type == TvType.CAST) "Google Cast" else "DLNA"
                        connecting -> "Bağlanıyor…"
                        else -> if (d.type == TvType.CAST) "Google Cast" else "DLNA • ${d.description}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            when {
                connecting -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                connected -> FilledTonalButton(onClick = { Remote.disconnect() }) {
                    Icon(Icons.Rounded.LinkOff, null, Modifier.size(18.dp))
                    Gap(6.dp)
                    Text("Kes")
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    e: Entry,
    lib: MediaLibrary,
    playingNow: Boolean,
    progress: Float?,
    watched: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ExpressiveCard(
        modifier = modifier,
        color = if (playingNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (e.kind == Kind.FOLDER) {
                IconBadge(Icons.Rounded.Folder, size = 48.dp)
            } else {
                Thumb(e, lib)
            }
            Gap(14.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    (if (watched) "✓ " else "") + e.title,
                    style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val sub = when {
                    e.kind == Kind.FOLDER -> if (e.childCount >= 0) "${e.childCount} öğe" else ""
                    e.kind == Kind.AUDIO -> listOfNotNull(e.artist, e.durationMs.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" • ")
                    else -> listOfNotNull(e.durationMs.takeIf { it > 0 }?.let(::formatDuration), e.size.takeIf { it > 0 }?.let(::formatBytes)).joinToString(" • ")
                }
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                AnimatedVisibility(progress != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(MaterialTheme.shapes.small),
                    )
                }
            }
        }
    }
}

@Composable
private fun Thumb(e: Entry, lib: MediaLibrary) {
    val bmp by produceState<ImageBitmap?>(null, e.id) {
        value = withContext(Dispatchers.IO) {
            runCatching { lib.thumbnail(e.id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) {
        Image(b, null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(percent = 28)))
    } else {
        IconBadge(
            when (e.kind) { Kind.AUDIO -> Icons.Rounded.MusicNote; Kind.IMAGE -> Icons.Rounded.Image; else -> Icons.Rounded.Movie },
            size = 56.dp,
        )
    }
}
