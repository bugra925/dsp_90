package app.dsp90

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder

class EffectService : Service() {
    private val rx = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val s = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            if (s < 0) return
            if (i.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) Engine.attach(s) else Engine.detach(s)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("dsp", "DSP-90", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "dsp")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("DSP-90 çalışıyor")
            .setContentText("Ses efekti açık")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(1, n)
        Engine.attach(0)
        val f = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(rx, f, Context.RECEIVER_EXPORTED) else registerReceiver(rx, f)
    }

    override fun onStartCommand(i: Intent?, flags: Int, id: Int): Int = START_STICKY

    override fun onDestroy() {
        try { unregisterReceiver(rx) } catch (_: Throwable) {}
        Engine.releaseAll()
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
