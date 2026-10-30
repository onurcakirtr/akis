package com.akis.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.RemoteViews
import com.akis.app.Prefs
import com.akis.app.R
import com.akis.app.ServerState
import com.akis.app.cast.Remote
import com.akis.app.cast.RemoteState
import com.akis.app.service.DlnaService
import com.akis.app.ui.MainActivity
import com.akis.app.ui.formatBytes
import com.akis.app.ui.formatDuration

/** Ana ekran widget'ları: küçük (aç/kapat) ve büyük (durum + TV kumandası). */
object Widgets {
    const val ACTION_CONTROL = "com.akis.app.CONTROL"
    const val EXTRA_CMD = "cmd"

    fun refresh(ctx: Context) {
        if (!Prefs.current.widget) return
        val mgr = AppWidgetManager.getInstance(ctx)
        val small = mgr.getAppWidgetIds(ComponentName(ctx, SmallWidget::class.java))
        val large = mgr.getAppWidgetIds(ComponentName(ctx, LargeWidget::class.java))
        if (small.isNotEmpty()) mgr.updateAppWidget(small, small(ctx))
        if (large.isNotEmpty()) mgr.updateAppWidget(large, large(ctx))
    }

    /** Ayar kapatılınca widget'lar seçme listesinden de kalkar. */
    fun setEnabled(ctx: Context, on: Boolean) {
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        for (cls in listOf(SmallWidget::class.java, LargeWidget::class.java)) {
            ctx.packageManager.setComponentEnabledSetting(ComponentName(ctx, cls), state, PackageManager.DONT_KILL_APP)
        }
        if (on) refresh(ctx)
    }

    // ---------------- Görünüm ----------------

    private fun statusText(running: Boolean): String {
        if (!running) return "Kapalı"
        val clients = ServerState.clients.value.size
        return if (clients > 0) "Yayında • $clients cihaz" else "Yayında"
    }

    private fun power(ctx: Context, v: RemoteViews, running: Boolean) {
        v.setInt(R.id.w_power_bg, "setBackgroundResource", if (running) R.drawable.widget_btn_on else R.drawable.widget_btn_off)
        v.setInt(R.id.w_power, "setColorFilter", if (running) 0xFFFFFFFF.toInt() else ctx.getColor(R.color.widget_fg))
        v.setOnClickPendingIntent(R.id.w_power_bg, toggleIntent(ctx, running))
        v.setOnClickPendingIntent(R.id.w_root, openIntent(ctx, false))
    }

    private fun small(ctx: Context): RemoteViews {
        val running = ServerState.running.value
        val rs = Remote.state.value
        return RemoteViews(ctx.packageName, R.layout.widget_small).apply {
            setTextViewText(
                R.id.w_status,
                when {
                    rs.hasMedia -> "▶ ${rs.title}"
                    else -> statusText(running)
                },
            )
            power(ctx, this, running)
        }
    }

    private fun large(ctx: Context): RemoteViews {
        val running = ServerState.running.value
        val rs = Remote.state.value
        val stream = ServerState.streams.value.firstOrNull()
        return RemoteViews(ctx.packageName, R.layout.widget_large).apply {
            setImageViewResource(R.id.w_dot, if (running) R.drawable.widget_dot_on else R.drawable.widget_dot_off)
            setTextViewText(R.id.w_status, statusText(running))
            power(ctx, this, running)
            when {
                rs.hasMedia -> {
                    setTextViewText(R.id.w_now, rs.title)
                    setTextViewText(
                        R.id.w_sub,
                        "${rs.device?.name ?: ""} • ${formatDuration(rs.positionMs)} / ${formatDuration(rs.durationMs)}" +
                            if (rs.buffering) " • yükleniyor" else "",
                    )
                    setProgressBar(R.id.w_progress, 1000, progress(rs), false)
                    setViewVisibility(R.id.w_progress, View.VISIBLE)
                    setViewVisibility(R.id.w_controls, View.VISIBLE)
                    setImageViewResource(R.id.w_play, if (rs.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)
                    for ((id, cmd) in listOf(
                        R.id.w_voldown to "voldown", R.id.w_rew to "rew", R.id.w_play to "toggle",
                        R.id.w_fwd to "fwd", R.id.w_stop to "stop", R.id.w_volup to "volup",
                    )) setOnClickPendingIntent(id, controlIntent(ctx, cmd))
                    setOnClickPendingIntent(R.id.w_root, openIntent(ctx, true))
                }
                stream != null -> {
                    setTextViewText(R.id.w_now, stream.title)
                    setTextViewText(R.id.w_sub, "${stream.client} • ${formatBytes(stream.position)} / ${formatBytes(stream.size)}")
                    val p = if (stream.size > 0) (stream.position * 1000 / stream.size).toInt() else 0
                    setProgressBar(R.id.w_progress, 1000, p, false)
                    setViewVisibility(R.id.w_progress, View.VISIBLE)
                    setViewVisibility(R.id.w_controls, View.GONE)
                }
                else -> {
                    setTextViewText(R.id.w_now, if (running) "Şu an bir şey oynatılmıyor" else "Yayın kapalı")
                    setTextViewText(
                        R.id.w_sub,
                        if (running) ServerState.address.value?.removePrefix("http://") ?: "Ağ bekleniyor…" else "Açmak için düğmeye dokun",
                    )
                    setViewVisibility(R.id.w_progress, View.GONE)
                    setViewVisibility(R.id.w_controls, View.GONE)
                }
            }
        }
    }

    fun progress(rs: RemoteState) = if (rs.durationMs > 0) (rs.positionMs * 1000 / rs.durationMs).toInt().coerceIn(0, 1000) else 0

    // ---------------- Tıklamalar ----------------

    private fun toggleIntent(ctx: Context, running: Boolean): PendingIntent = if (running) {
        PendingIntent.getService(
            ctx, 10, Intent(ctx, DlnaService::class.java).setAction(DlnaService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    } else {
        PendingIntent.getForegroundService(
            ctx, 11, Intent(ctx, DlnaService::class.java).setAction(DlnaService.ACTION_START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun openIntent(ctx: Context, send: Boolean): PendingIntent = PendingIntent.getActivity(
        ctx, if (send) 13 else 12,
        Intent(ctx, MainActivity::class.java)
            .setAction(if (send) MainActivity.ACTION_OPEN_SEND else Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Widget ve bildirimdeki kumanda düğmeleri. */
    fun controlIntent(ctx: Context, cmd: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, 100 + cmd.hashCode() % 1000,
        Intent(ctx, ControlReceiver::class.java).setAction(ACTION_CONTROL).putExtra(EXTRA_CMD, cmd),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun run(cmd: String) {
        when (cmd) {
            "toggle" -> Remote.togglePlay()
            "rew" -> Remote.skip(-10_000)
            "fwd" -> Remote.skip(30_000)
            "stop" -> Remote.stop()
            "volup" -> Remote.volumeStep(true)
            "voldown" -> Remote.volumeStep(false)
        }
    }
}

class SmallWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = Widgets.refresh(context)
}

class LargeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = Widgets.refresh(context)
}

class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Widgets.run(intent.getStringExtra(Widgets.EXTRA_CMD) ?: return)
    }
}
