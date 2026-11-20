package com.akis.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.BuildConfig
import com.akis.app.Prefs
import com.akis.app.R
import com.akis.app.Updater
import kotlinx.coroutines.launch

/** Ana ekranda: yeni sürüm varken, indirirken ya da kurulum başarısız olunca görünür. */
@Composable
fun UpdateCard(modifier: Modifier = Modifier) {
    val st by Updater.state.collectAsStateWithLifecycle()
    if (st !is Updater.State.Available && st !is Updater.State.Downloading && st !is Updater.State.Installing && st !is Updater.State.Failed) return
    ExpressiveCard(modifier = modifier, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.SystemUpdate, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { UpdateStatus(st, showIdle = true) }
        }
    }
}

/** Ayarlar → Hakkında: sürüm, [description], denetim düğmesi ve otomatik denetim ayarı. */
@Composable
fun AboutVersion(description: String) {
    val st by Updater.state.collectAsStateWithLifecycle()
    val s by Prefs.settings.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val edition = if (BuildConfig.FULL) " · Full" else ""
    Text("Akış ${Updater.current}$edition", style = MaterialTheme.typography.titleMedium)
    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!Updater.supported) {
        Text(stringResource(R.string.upd_dev), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Spacer(Modifier.height(8.dp))
    UpdateStatus(st, showIdle = true)
    if (st !is Updater.State.Available && st !is Updater.State.Downloading && st !is Updater.State.Installing) {
        FilledTonalButton(
            onClick = { scope.launch { Updater.check(ctx.applicationContext, force = true) } },
            enabled = st !is Updater.State.Checking,
        ) { Text(stringResource(R.string.upd_check)) }
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.upd_auto), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.upd_auto_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(s.updateCheck, { v -> Prefs.set("updateCheck", v) })
    }
}

@Composable
private fun UpdateStatus(st: Updater.State, showIdle: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (st) {
        is Updater.State.Available -> {
            Text(stringResource(R.string.upd_available, st.version), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (Updater.ensureInstallPermission(ctx)) scope.launch { Updater.install(ctx.applicationContext, st) }
                }) { Text(stringResource(R.string.upd_install)) }
                if (st.page.isNotEmpty()) {
                    TextButton(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(st.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text(stringResource(R.string.upd_notes)) }
                }
            }
            Text(stringResource(R.string.upd_perm_note), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        is Updater.State.Downloading -> {
            Text(stringResource(R.string.upd_downloading, st.version, (st.progress * 100).toInt()), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth())
        }
        Updater.State.Installing -> Text(stringResource(R.string.upd_installing), style = MaterialTheme.typography.titleMedium)
        Updater.State.Checking -> if (showIdle) Text(stringResource(R.string.upd_checking), style = MaterialTheme.typography.bodySmall, color = muted)
        Updater.State.UpToDate -> if (showIdle) Text(stringResource(R.string.upd_latest), style = MaterialTheme.typography.bodySmall, color = muted)
        is Updater.State.Failed -> if (showIdle) Text(stringResource(R.string.upd_failed, st.message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Updater.State.Idle -> Unit
    }
}
