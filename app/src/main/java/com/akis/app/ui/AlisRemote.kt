package com.akis.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ConnectedTv
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.AlisLink
import com.akis.app.QuickShare
import com.akis.app.R
import com.akis.app.ServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Eşleşmiş bir Alış cihazının kumandası: oynat / duraklat, sarma (saniye cinsinden konum), önceki / sonraki,
 * hız, ses ve altyazı parçası, gecikmeler, ses düzeyi, durdur; telefondan bir videoyu o cihazda açma.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlisRemoteDialog(deviceId: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val devices by AlisLink.devices.collectAsStateWithLifecycle()
    val address by ServerState.address.collectAsStateWithLifecycle()
    val d = devices[deviceId]
    var dragging by remember { mutableStateOf<Float?>(null) }
    var sending by remember { mutableStateOf(false) }

    fun cmd(op: String, vararg kv: Pair<String, Any>) {
        val o = JSONObject().put("op", op)
        kv.forEach { (k, v) -> o.put(k, v) }
        AlisLink.send(deviceId, o)
    }

    // Telefondaki bir videoyu / şarkıyı Alış'ta aç: süreli paylaşım bağlantısı oluşturup adresini gönderir
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val base = address ?: return@rememberLauncherForActivityResult
        sending = true
        scope.launch {
            val mime = ctx.contentResolver.getType(uri)
            val sh = withContext(Dispatchers.IO) { QuickShare.create(ctx, uri, mime, 6 * 60, false) { } }
            sending = false
            if (sh != null) cmd("open", "url" to "$base/share/${sh.token}/dl", "title" to sh.name.substringBeforeLast('.'), "mime" to sh.mime)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth(0.94f)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.ConnectedTv, bg = MaterialTheme.colorScheme.primary, tint = MaterialTheme.colorScheme.onPrimary, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d?.name ?: stringResource(R.string.alis_remote), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(if (d?.online == true) R.string.alis_online else R.string.alis_offline),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
                val st = d?.state ?: JSONObject()
                val app = d?.app ?: "idle"
                if (app == "idle" || d == null) {
                    Text(stringResource(R.string.alis_idle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(d.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val count = st.optInt("count")
                    if (count > 1) Text("${st.optInt("index") + 1} / $count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val dur = d.durMs
                    val pos = d.posMs
                    if (dur > 0) {
                        Slider(
                            value = dragging ?: (pos.toFloat() / dur).coerceIn(0f, 1f),
                            onValueChange = { dragging = it },
                            onValueChangeFinished = { dragging?.let { f -> cmd("seek", "ms" to (f * dur).toLong()) }; dragging = null },
                        )
                        Row {
                            Text(formatDuration(dragging?.let { (it * dur).toLong() } ?: pos), style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.weight(1f))
                            Text(formatDuration(dur), style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { cmd("prev") }) { Icon(Icons.Rounded.SkipPrevious, null, Modifier.size(32.dp)) }
                        IconButton(onClick = { cmd("seekBy", "ms" to -10_000) }) { Icon(Icons.Rounded.Replay10, null, Modifier.size(32.dp)) }
                        FilledIconButton(onClick = { cmd("toggle") }, modifier = Modifier.size(64.dp)) {
                            Icon(if (d.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(36.dp))
                        }
                        IconButton(onClick = { cmd("seekBy", "ms" to 10_000) }) { Icon(Icons.Rounded.Forward10, null, Modifier.size(32.dp)) }
                        IconButton(onClick = { cmd("next") }) { Icon(Icons.Rounded.SkipNext, null, Modifier.size(32.dp)) }
                        IconButton(onClick = { cmd("stop") }) { Icon(Icons.Rounded.Stop, null, Modifier.size(28.dp)) }
                    }
                    if (app == "video") {
                        SectionTitle(stringResource(R.string.alis_speed))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val sp = st.optDouble("speed", 1.0)
                            listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { v ->
                                FilterChip(sp == v, { cmd("speed", "v" to v) }, label = { Text("${v}×") })
                            }
                        }
                        Tracks(stringResource(R.string.alis_audio), st.optJSONArray("audio")) { i -> cmd("audio", "i" to i) }
                        Tracks(stringResource(R.string.alis_subs), st.optJSONArray("subs"), offLabel = stringResource(R.string.alis_off)) { i -> cmd("sub", "i" to i) }
                        DelayRow(stringResource(R.string.alis_sub_delay), st.optLong("subDelay"), 100) { cmd("subDelay", "ms" to it) }
                        DelayRow(stringResource(R.string.alis_audio_delay), st.optLong("audioDelay"), 50) { cmd("audioDelay", "ms" to it) }
                    }
                }
                if (d != null && st.has("vol")) {
                    SectionTitle(stringResource(R.string.alis_volume))
                    var vol by remember(st.optInt("vol")) { mutableStateOf(st.optInt("vol").toFloat()) }
                    Slider(vol, { vol = it }, valueRange = 0f..100f, onValueChangeFinished = { cmd("volume", "v" to vol.toInt()) })
                }
                FilledTonalButton(onClick = { pick.launch(arrayOf("video/*", "audio/*")) }, enabled = !sending && address != null && d?.online == true, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.VideoLibrary, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (sending) R.string.alis_preparing else R.string.alis_open_from_phone))
                }
            }
        }
    }
}

/** Parça seçimi: Alış'ın gönderdiği [{i, label, sel}] listesi; offLabel verilirse "kapalı" (-1) da seçenek. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tracks(title: String, list: JSONArray?, offLabel: String? = null, onPick: (Int) -> Unit) {
    if ((list == null || list.length() == 0) && offLabel == null) return
    SectionTitle(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        var anySel = false
        if (list != null) for (k in 0 until list.length()) {
            val o = list.getJSONObject(k)
            val sel = o.optBoolean("sel")
            if (sel) anySel = true
            FilterChip(sel, { onPick(o.optInt("i")) }, label = { Text(o.optString("label"), maxLines = 1) })
        }
        if (offLabel != null) FilterChip(!anySel, { onPick(-1) }, label = { Text(offLabel) })
    }
}

@Composable
private fun DelayRow(title: String, value: Long, step: Long, onSet: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onSet(value - step) }) { Text("−$step") }
        Text("$value ms", style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { onSet(value + step) }) { Text("+$step") }
        if (value != 0L) TextButton(onClick = { onSet(0) }, shape = CircleShape) { Text("0") }
    }
    Spacer(Modifier.height(0.dp))
}
