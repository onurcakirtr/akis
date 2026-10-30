package com.akis.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.Prefs
import com.akis.app.dlna.Kind
import com.akis.app.dlna.MediaLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val MEDIA_PERMISSIONS: Array<String> =
    if (android.os.Build.VERSION.SDK_INT >= 33) arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_IMAGES,
    ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

private fun permFor(kind: Kind): String = if (android.os.Build.VERSION.SDK_INT < 33) Manifest.permission.READ_EXTERNAL_STORAGE
else when (kind) {
    Kind.VIDEO -> Manifest.permission.READ_MEDIA_VIDEO
    Kind.AUDIO -> Manifest.permission.READ_MEDIA_AUDIO
    else -> Manifest.permission.READ_MEDIA_IMAGES
}

@Composable
fun SourcesScreen() {
    val ctx = LocalContext.current
    val settings by Prefs.settings.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }

    fun granted(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val hasVideo = remember(refresh) { granted(permFor(Kind.VIDEO)) }
    val hasAudio = remember(refresh) { granted(permFor(Kind.AUDIO)) }
    val hasImage = remember(refresh) { granted(permFor(Kind.IMAGE)) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Prefs.addFolder(uri.toString())
            } catch (_: Exception) {
            }
        }
    }

    val counts by produceState(Triple(-1, -1, -1), refresh) {
        value = withContext(Dispatchers.IO) {
            val lib = MediaLibrary(ctx)
            Triple(lib.count(Kind.VIDEO), lib.count(Kind.AUDIO), lib.count(Kind.IMAGE))
        }
    }
    val folderNames by produceState(emptyList<Pair<String, String>>(), settings.folders) {
        value = withContext(Dispatchers.IO) { settings.folders.map { it to MediaLibrary.treeName(ctx, it) } }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Kaynaklar", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 4.dp))
            Text(
                "Ağda neyin görüneceğini seç",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }

        if (!hasVideo || !hasAudio || !hasImage) {
            item {
                ExpressiveCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Lock, bg = MaterialTheme.colorScheme.tertiary, tint = MaterialTheme.colorScheme.onTertiary)
                        Spacer(Modifier.padding(start = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Medya izni gerekli", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(
                                "Videolarını, müziklerini ve fotoğraflarını listeleyebilmem için izin ver.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { permLauncher.launch(MEDIA_PERMISSIONS) }) { Text("İzin ver") }
                        FilledTonalButton(onClick = {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }) { Text("Ayarlar") }
                    }
                }
            }
        }

        item { SectionTitle("Medya kitaplığı") }
        item {
            CategoryCard(Icons.Rounded.Movie, "Videolar", countText(counts.first, hasVideo, "video"), settings.shareVideo) {
                Prefs.update { putBoolean("shareVideo", it) }
            }
        }
        item {
            CategoryCard(Icons.Rounded.MusicNote, "Müzik", countText(counts.second, hasAudio, "şarkı"), settings.shareAudio) {
                Prefs.update { putBoolean("shareAudio", it) }
            }
        }
        item {
            CategoryCard(Icons.Rounded.Image, "Fotoğraflar", countText(counts.third, hasImage, "fotoğraf"), settings.shareImage) {
                Prefs.update { putBoolean("shareImage", it) }
            }
        }
        item {
            CategoryCard(Icons.Rounded.AutoAwesome, "Son eklenenler", "En yeni video ve fotoğraflar tek klasörde", settings.showRecent) {
                Prefs.update { putBoolean("showRecent", it) }
            }
        }

        item { SectionTitle("Klasörlerim") }
        item {
            Text(
                "İstediğin klasörü (indirilenler, USB bellek, SD kart…) olduğu gibi paylaş. Videoyla aynı adlı .srt altyazılar da otomatik eklenir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        items(folderNames, key = { it.first }) { (uri, name) ->
            ExpressiveCard(modifier = Modifier.animateItem()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Folder, size = 44.dp)
                    Spacer(Modifier.padding(start = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            Uri.decode(uri).substringAfterLast("tree/").substringBefore('/'),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = {
                        try {
                            ctx.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Exception) {
                        }
                        Prefs.removeFolder(uri)
                    }) { Icon(Icons.Rounded.Delete, "Kaldır") }
                }
            }
        }
        item {
            FilledTonalButton(
                onClick = { folderLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Rounded.CreateNewFolder, null)
                Spacer(Modifier.padding(start = 8.dp))
                Text("Klasör ekle")
            }
        }
    }
}

private fun countText(n: Int, granted: Boolean, unit: String) = when {
    !granted -> "İzin verilmedi"
    n < 0 -> "Sayılıyor…"
    else -> "$n $unit"
}

@Composable
private fun CategoryCard(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ExpressiveCard(onClick = { onChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon,
                bg = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                tint = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(start = 14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
