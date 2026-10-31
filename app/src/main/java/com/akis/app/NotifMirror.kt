package com.akis.app

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * Bildirim yansıtma: telefona gelen son 50 bildirim yalnızca bellekte tutulur, web'de gösterilir.
 * Android'in "Bildirim erişimi" izni kullanıcı tarafından sistem ayarlarından verilir.
 */
object NotifMirror {
    data class Item(
        val key: String,
        val pkg: String,
        val app: String,
        val title: String,
        val text: String,
        val time: Long,
        val gone: Boolean = false,
    )

    private val list = ArrayList<Item>()
    @Volatile var version = 0
        private set

    fun granted(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun items(): List<Item> = synchronized(list) { list.toList() }

    fun clear() = synchronized(list) { list.clear(); version++ }

    internal fun posted(ctx: Context, sbn: StatusBarNotification) {
        if (!Prefs.current.notifs || sbn.packageName == ctx.packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return
        val app = try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (_: Exception) {
            sbn.packageName
        }
        synchronized(list) {
            list.removeAll { it.key == sbn.key }
            list.add(0, Item(sbn.key, sbn.packageName, app, title.take(200), text.take(1000), sbn.postTime))
            while (list.size > 50) list.removeAt(list.size - 1)
            version++
        }
    }

    internal fun removed(sbn: StatusBarNotification) = synchronized(list) {
        val i = list.indexOfFirst { it.key == sbn.key }
        if (i >= 0) { list[i] = list[i].copy(gone = true); version++ }
    }

    /** İzin verildiyse dinleyiciyi yeniden bağlar (ayar açıldığında). */
    fun rebind(ctx: Context) {
        runCatching { NotificationListenerService.requestRebind(ComponentName(ctx, NotifListener::class.java)) }
    }
}

class NotifListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) = NotifMirror.posted(this, sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotifMirror.removed(sbn)
    }
}
