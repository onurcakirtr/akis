package com.akis.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * Telefonu çaldır (yalnızca web yönetim sayfasından): alarm sesiyle, sessiz modda bile 30 saniye.
 * Alarm ses düzeyi geçici olarak en yükseğe alınır, bitince eski hâline döner. Bildirimdeki "Durdur" ile kesilir.
 */
object Ringer {
    const val ACTION_STOP = "com.akis.app.RING_STOP"
    private const val NOTIF_ID = 3500
    private const val DURATION = 30_000L

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var oldVolume = -1
    private lateinit var app: Context

    @Volatile var active = false
        private set

    fun start(ctx: Context, from: String) = main.post {
        app = ctx.applicationContext
        if (active) return@post
        val am = app.getSystemService(AudioManager::class.java)
        oldVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(app, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
            null
        }
        runCatching {
            app.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0))
        }
        active = true
        notifyRinging(from)
        ServerState.log(Lang.s(R.string.log_ring, from))
        main.postDelayed({ stop() }, DURATION)
    }

    fun stop() {
        main.post {
            main.removeCallbacksAndMessages(null)
            if (!active) return@post
            active = false
            player?.runCatching { stop(); release() }
            player = null
            runCatching { app.getSystemService(Vibrator::class.java)?.cancel() }
            val am = app.getSystemService(AudioManager::class.java)
            if (oldVolume >= 0) runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, oldVolume, 0) }
            oldVolume = -1
            app.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        }
    }

    private fun notifyRinging(from: String) {
        val stop = PendingIntent.getBroadcast(
            app, NOTIF_ID, Intent(app, RingReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, AkisApplication.CHANNEL_ACCESS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Lang.s(R.string.ring_title))
            .setContentText(Lang.s(R.string.ring_text, from))
            .setColor(0xFFD71921.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(stop)
            .setDeleteIntent(stop)
            .addAction(0, Lang.s(R.string.ring_stop), stop)
            .build()
        try { app.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n) } catch (_: SecurityException) {}
    }
}

class RingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Ringer.ACTION_STOP) Ringer.stop()
    }
}
