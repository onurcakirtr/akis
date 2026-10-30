@file:OptIn(ExperimentalMaterial3Api::class)

package com.akis.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.Prefs
import com.akis.app.ServerState
import com.akis.app.service.DlnaService

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val s by Prefs.settings.collectAsStateWithLifecycle()
    val running by ServerState.running.collectAsStateWithLifecycle()
    var editName by remember { mutableStateOf(false) }
    var editPort by remember { mutableStateOf(false) }
    val startedAt by ServerState.startedAt.collectAsStateWithLifecycle()
    // Sunucu açıkken port değişirse yeniden başlatma öner
    val portAtStart = remember(startedAt, running) { s.port }
    val needsRestart = running && portAtStart != s.port

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Ayarlar", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp))
        }

        if (needsRestart) {
            item {
                ExpressiveCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Yeni port için yeniden başlat", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { DlnaService.restart(ctx) }) {
                        Icon(Icons.Rounded.RestartAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Yeniden başlat")
                    }
                }
            }
        }

        item { SectionTitle("Sunucu") }
        item {
            ExpressiveCard {
                SettingRow(Icons.Rounded.Smartphone, "Ağdaki adı", s.name, onClick = { editName = true }) {
                    Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider()
                SettingRow(Icons.Rounded.Router, "Port", s.port.toString(), onClick = { editPort = true }) {
                    Icon(Icons.Rounded.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Divider()
                SettingRow(
                    Icons.Rounded.Language, "Web arayüzü", "Tarayıcıdan izlemeye izin ver",
                    onClick = { Prefs.update { putBoolean("webUi", !s.webUi) } },
                ) { Switch(s.webUi, { v -> Prefs.update { putBoolean("webUi", v) } }) }
            }
        }

        item { SectionTitle("Davranış") }
        item {
            ExpressiveCard {
                SettingRow(
                    Icons.Rounded.Bolt, "Uyanık tut", "Ekran kapalıyken yayın kesilmesin",
                    onClick = { Prefs.update { putBoolean("keepAwake", !s.keepAwake) } },
                ) { Switch(s.keepAwake, { v -> Prefs.update { putBoolean("keepAwake", v) } }) }
                Divider()
                SettingRow(
                    Icons.Rounded.RestartAlt, "Açılışta başlat", "Uygulamayı açınca yayın kendiliğinden başlasın",
                    onClick = { Prefs.update { putBoolean("autoStart", !s.autoStart) } },
                ) { Switch(s.autoStart, { v -> Prefs.update { putBoolean("autoStart", v) } }) }
            }
        }

        item { SectionTitle("Özellikler") }
        item {
            ExpressiveCard {
                FeatureRow(
                    Icons.Rounded.VisibilityOff, "Gizli klasörler",
                    ".nomedia içeren ve Kaynaklar'da gizlediğin klasörler paylaşılmasın",
                    s.hideFolders,
                ) { Prefs.set("hideFolders", it) }
                // FEATURES
            }
        }

        item { SectionTitle("Görünüm") }
        item {
            ExpressiveCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Palette, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Tema", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                Segmented(
                    options = listOf("material" to "Material You", "nothing" to "Nothing"),
                    selected = s.theme,
                ) { v -> Prefs.update { putString("theme", v) } }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.DarkMode, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Text("Koyu mod", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                Segmented(
                    options = listOf("system" to "Sistem", "light" to "Açık", "dark" to "Koyu"),
                    selected = s.darkMode,
                ) { v -> Prefs.update { putString("darkMode", v) } }
            }
        }

        item { SectionTitle("Hakkında") }
        item {
            ExpressiveCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Info, size = 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Akış 1.0", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Reklamsız, internetsiz UPnP/DLNA medya sunucusu. Her şey yerel ağında kalır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (editName) {
        TextDialog("Ağdaki adı", s.name, KeyboardType.Text, onDismiss = { editName = false }) { v ->
            Prefs.update { putString("name", v.trim()) }
            editName = false
        }
    }
    if (editPort) {
        TextDialog("Port (1024–65535)", s.port.toString(), KeyboardType.Number, onDismiss = { editPort = false }) { v ->
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
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
