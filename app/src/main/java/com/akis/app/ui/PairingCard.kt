package com.akis.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.Pairing
import com.akis.app.Prefs
import com.akis.app.R
import com.akis.app.ServerState
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Ayarlar → Alış: eşleştirme kodu ve eşleşmiş cihazlar (cihaz başına dosya yönetimi izni). */
@Composable
fun PairingCard() {
    val devices by Pairing.devices.collectAsStateWithLifecycle()
    val code by Pairing.code.collectAsStateWithLifecycle()
    val address by ServerState.address.collectAsStateWithLifecycle()
    val s by Prefs.settings.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<Pairing.Device?>(null) }

    ExpressiveCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Devices)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pair_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.pair_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(10.dp))
        FilledTonalButton(onClick = { Pairing.newCode() }, enabled = address != null) {
            Icon(Icons.Rounded.QrCode2, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.pair_add))
        }
        if (address == null) {
            Text(stringResource(R.string.pair_need_server), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (devices.isEmpty()) {
            Text(
                stringResource(R.string.pair_none), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (devices.isNotEmpty() && !s.fileOps) {
            Text(
                stringResource(R.string.pair_fileops_off), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
            )
        }
        devices.forEach { d ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.pair_last, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(d.lastSeen))),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { removing = d }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.pair_remove)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.f_fileops), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(d.fileOps, { v -> Pairing.setFileOps(d.id, v) }, enabled = s.fileOps)
            }
        }
    }

    val c = code
    if (c != null && address != null) {
        var left by remember(c) { mutableLongStateOf((c.second - System.currentTimeMillis()) / 1000) }
        LaunchedEffect(c) {
            while (left > 0) {
                delay(1000)
                left = (c.second - System.currentTimeMillis()) / 1000
            }
            Pairing.cancelCode()
        }
        val qr = "alis://pair?u=" + Uri.encode(address) + "&c=" + c.first + "&n=" + Uri.encode(s.name)
        AlertDialog(
            onDismissRequest = { Pairing.cancelCode() },
            title = { Text(stringResource(R.string.pair_code_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DotQrCode(qr, Modifier.size(200.dp))
                    Text(
                        c.first.chunked(3).joinToString(" "), style = MaterialTheme.typography.displaySmall,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    )
                    Text(address ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.pair_code_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%d:%02d".format(left.coerceAtLeast(0) / 60, left.coerceAtLeast(0) % 60), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = { TextButton(onClick = { Pairing.cancelCode() }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    removing?.let { d ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.pair_remove)) },
            text = { Text(d.name) },
            confirmButton = { TextButton(onClick = { Pairing.remove(d.id); removing = null }) { Text(stringResource(R.string.pair_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
