package com.akis.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.akis.app.R
import com.akis.app.ui.theme.Backdrop
import com.akis.app.ui.theme.LocalAppTheme
import kotlinx.coroutines.launch

private class Page(val icon: ImageVector, val title: Int, val text: Int, val extra: Int? = null)

private val PAGES = listOf(
    Page(Icons.Rounded.Tv, R.string.ob_1_title, R.string.ob_1_text),
    Page(Icons.Rounded.PowerSettingsNew, R.string.ob_2_title, R.string.ob_2_text),
    Page(Icons.Rounded.VideoLibrary, R.string.ob_3_title, R.string.ob_3_text, R.string.ob_3_extra),
    Page(Icons.Rounded.Language, R.string.ob_4_title, R.string.ob_4_text),
    Page(Icons.Rounded.Lock, R.string.ob_5_title, R.string.ob_5_text),
    Page(Icons.Rounded.Shield, R.string.ob_6_title, R.string.ob_6_text, R.string.ob_6_extra),
)

/** İlk açılış tanıtımı; Ayarlar → Hakkında → Tanıtımı yeniden göster ile tekrar açılır. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val theme = LocalAppTheme.current
    val cs = MaterialTheme.colorScheme
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    var permTick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val wanted = remember {
        buildList {
            addAll(MEDIA_PERMISSIONS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val granted = remember(permTick) { wanted.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED } }

    Box(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .backdrop(theme.backdrop, (if (theme.backdrop == Backdrop.DOTS) cs.onSurface else cs.primary).copy(alpha = theme.backdropAlpha))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveDot(true, 12.dp)
                Spacer(Modifier.width(10.dp))
                Text("Akış", style = MaterialTheme.typography.titleLarge, color = cs.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onDone) { Text(stringResource(R.string.ob_skip)) }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
                val p = PAGES[i]
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconBadge(p.icon, size = 112.dp)
                    Spacer(Modifier.height(28.dp))
                    Text(stringResource(p.title), style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(p.text), style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    p.extra?.let {
                        Spacer(Modifier.height(16.dp))
                        ExpressiveCard(color = cs.surfaceContainerHigh) {
                            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (i == 4) {
                        Spacer(Modifier.height(20.dp))
                        if (granted) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = cs.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.ob_perm_ok), color = cs.primary)
                            }
                        } else {
                            Button(onClick = { launcher.launch(wanted.toTypedArray()) }) { Text(stringResource(R.string.allow)) }
                        }
                    }
                }
            }
            // Sayfa noktaları + ileri
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(PAGES.size) { k ->
                        val on = pager.currentPage == k
                        val w by animateDpAsState(if (on) 26.dp else 8.dp, spring(dampingRatio = 0.6f), label = "dot")
                        val c by animateColorAsState(if (on) cs.primary else cs.outlineVariant, label = "dotc")
                        Box(Modifier.height(8.dp).width(w).clip(CircleShape).background(c))
                    }
                }
                val last = pager.currentPage == PAGES.lastIndex
                if (last) {
                    Button(onClick = onDone) { Text(stringResource(R.string.ob_start)) }
                } else {
                    FilledTonalButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(stringResource(R.string.ob_next))
                    }
                }
            }
        }
    }
}
