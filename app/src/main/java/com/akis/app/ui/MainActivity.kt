package com.akis.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akis.app.Prefs
import com.akis.app.ServerState
import com.akis.app.service.DlnaService
import com.akis.app.ui.theme.AkisTheme
import com.akis.app.ui.theme.LocalNothingStyle

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        handleIntent(intent)
        setContent {
            val s by Prefs.settings.collectAsStateWithLifecycle()
            val dark = when (s.darkMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            DisposableEffect(dark) {
                val t = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            AkisTheme(theme = s.theme, dark = dark) { AppRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == DlnaService.ACTION_START && !ServerState.running.value) DlnaService.start(this)
    }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("Yayın", Icons.Rounded.Home),
    Tab("Kaynaklar", Icons.Rounded.VideoLibrary),
    Tab("Ayarlar", Icons.Rounded.Settings),
)

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val nothing = LocalNothingStyle.current
    val cs = MaterialTheme.colorScheme

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val wanted = buildList {
            addAll(MEDIA_PERMISSIONS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) launcher.launch(wanted.toTypedArray())
        if (Prefs.current.autoStart && !ServerState.running.value) DlnaService.start(ctx)
    }

    Scaffold(
        containerColor = cs.background,
        bottomBar = {
            NavigationBar(containerColor = if (nothing) cs.background else cs.surfaceContainer) {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .dotGrid(cs.onSurface.copy(alpha = if (nothing) 0.13f else 0.05f))
                .padding(pad),
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(spring(stiffness = 400f)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 400f), initialScale = 0.94f)) togetherWith
                        fadeOut(spring(stiffness = 800f))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> HomeScreen()
                    1 -> SourcesScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}
