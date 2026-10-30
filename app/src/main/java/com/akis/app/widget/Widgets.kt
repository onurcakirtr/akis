package com.akis.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.RemoteViews
import com.akis.app.Prefs
import com.akis.app.Lang
import com.akis.app.R
import com.akis.app.ServerState
import com.akis.app.service.DlnaService
import com.akis.app.ui.MainActivity
import com.akis.app.ui.formatBytes

/** Ana ekran widget'ları: küçük (aç/kapat) ve büyük (durum + oynatılan dosya). */
object Widgets {
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
        if (!running) return Lang.s(R.string.status_off)
        val clients = ServerState.clients.value.size
        return if (clients > 0) Lang.s(R.string.status_on_devices, Lang.q(R.plurals.count_devices, clients)) else Lang.s(R.string.status_on)
    }

    private fun power(ctx: Context, v: RemoteViews, running: Boolean) {
        v.setInt(R.id.w_power_bg, "setBackgroundResource", if (running) R.drawable.widget_btn_on else R.drawable.widget_btn_off)
        v.setInt(R.id.w_power, "setColorFilter", if (running) 0xFFFFFFFF.toInt() else ctx.getColor(R.color.widget_fg))
        v.setOnClickPendingIntent(R.id.w_power_bg, toggleIntent(ctx, running))
        v.setOnClickPendingIntent(R.id.w_root, openIntent(ctx))
    }

    private fun small(ctx: Context): RemoteViews {
        val running = ServerState.running.value
        val stream = ServerState.streams.value.firstOrNull()
        return RemoteViews(ctx.packageName, R.layout.widget_small).apply {
            setTextViewText(R.id.w_status, if (running && stream != null) "▶ ${stream.title}" else statusText(running))
            power(ctx, this, running)
        }
    }

    private fun large(ctx: Context): RemoteViews {
        val running = ServerState.running.value
        val stream = ServerState.streams.value.firstOrNull()
        return RemoteViews(ctx.packageName, R.layout.widget_large).apply {
            setImageViewResource(R.id.w_dot, if (running) R.drawable.widget_dot_on else R.drawable.widget_dot_off)
            setTextViewText(R.id.w_status, statusText(running))
            power(ctx, this, running)
            when {
                stream != null -> {
                    setTextViewText(R.id.w_now, stream.title)
                    setTextViewText(R.id.w_sub, "${stream.client} • ${formatBytes(stream.position)} / ${formatBytes(stream.size)}")
                    val p = if (stream.size > 0) (stream.position * 1000 / stream.size).toInt() else 0
                    setProgressBar(R.id.w_progress, 1000, p, false)
                    setViewVisibility(R.id.w_progress, View.VISIBLE)
                }
                else -> {
                    setTextViewText(R.id.w_now, if (running) Lang.s(R.string.nothing_playing) else Lang.s(R.string.sharing_off))
                    setTextViewText(
                        R.id.w_sub,
                        if (running) ServerState.address.value?.removePrefix("http://") ?: Lang.s(R.string.waiting_network) else Lang.s(R.string.tap_to_turn_on),
                    )
                    setViewVisibility(R.id.w_progress, View.GONE)
                }
            }
        }
    }

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

    private fun openIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 12,
        Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

}

class SmallWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = Widgets.refresh(context)
}

class LargeWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = Widgets.refresh(context)
}
