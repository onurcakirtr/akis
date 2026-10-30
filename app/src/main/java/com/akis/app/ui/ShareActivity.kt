package com.akis.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.Prefs
import com.akis.app.QuickShare
import com.akis.app.R
import com.akis.app.ServerState
import com.akis.app.service.DlnaService
import com.akis.app.ui.theme.AkisTheme
import com.akis.app.ui.theme.Themes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Paylaş → Akış: dosya için süreli indirme bağlantısı + QR kod. */
class ShareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        QuickShare.init(this)
        @Suppress("DEPRECATION")
        val uri: Uri? = intent?.getParcelableExtra(Intent.EXTRA_STREAM)
        if (uri == null) { finish(); return }
        val type = intent?.type
        setContent {
            val s by Prefs.settings.collectAsStateWithLifecycle()
            val theme = Themes.byId(s.theme)
            AkisTheme(theme = theme, dark = theme.isDark(s.darkMode, isSystemInDarkTheme())) {
                ShareSheet(uri, type) { finish() }
            }
        }
    }
}

@Composable
private fun ShareSheet(uri: Uri, type: String?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = remember { QuickShare.nameOf(ctx, uri) }
    var minutes by remember { mutableStateOf(60) }
    var once by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var copied by remember { mutableLongStateOf(0L) }
    var token by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val address by ServerState.address.collectAsStateWithLifecycle()
    val noRipple = remember { MutableInteractionSource() }

    // Dışarı dokununca kapan
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(noRipple, null) { if (!busy) onClose() },
        contentAlignment = Alignment.Center,
    ) {
        ExpressiveCard(
            modifier = Modifier.padding(20.dp).widthIn(max = 420.dp).clickable(noRipple, null) { },
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.Link, size = 44.dp)
                Gap(14.dp)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        info.first + if (info.second > 0) " • " + formatBytes(info.second) else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            val tk = token
            if (tk == null) {
                Text(stringResource(R.string.share_duration), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15 to R.string.share_15m, 60 to R.string.share_1h, 24 * 60 to R.string.share_1d).forEach { (m, label) ->
                        FilterChip(selected = minutes == m, onClick = { if (!busy) minutes = m }, label = { Text(stringResource(label)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = !busy) { once = !once }) {
                    Checkbox(checked = once, onCheckedChange = { if (!busy) once = it })
                    Text(stringResource(R.string.share_once), style = MaterialTheme.typography.bodyMedium)
                }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    if (info.second > 0) LinearProgressIndicator(progress = { (copied.toFloat() / info.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.share_copying), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            if (!ServerState.running.value) DlnaService.start(ctx)
                            val s = withContext(Dispatchers.IO) { QuickShare.create(ctx, uri, type, minutes, once) { copied = it } }
                            // Sunucu açılıp adres alana kadar bekle
                            withTimeoutOrNull(8000) { while (ServerState.address.value == null) delay(200) }
                            busy = false
                            if (s == null) error = ctx.getString(R.string.share_failed) else token = s.token
                        }
                    }) { Text(stringResource(R.string.share_create)) }
                    TextButton(enabled = !busy, onClick = onClose) { Text(stringResource(R.string.cancel)) }
                }
            } else {
                val url = address?.let { "$it/share/$tk" }
                if (url == null) {
                    Text(stringResource(R.string.waiting_network), color = MaterialTheme.colorScheme.error)
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(14.dp)) {
                            DotQrCode(url, Modifier.size(220.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(url, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.share_hint),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Akış", url))
                        }) {
                            Icon(Icons.Rounded.ContentCopy, null)
                            Gap(8.dp)
                            Text(stringResource(R.string.copy))
                        }
                        TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
    }
}
