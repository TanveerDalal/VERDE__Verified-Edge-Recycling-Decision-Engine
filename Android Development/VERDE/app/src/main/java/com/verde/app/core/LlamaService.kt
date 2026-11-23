package com.verde.app.core

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.verde.app.ui.ask.AskActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps Verde's Llama answering WhatsApp even when the screen is off or you use another app.
 *
 * Without it, Android pauses Verde as soon as it leaves the screen, so the WhatsApp server
 * waits for an answer that never comes. A foreground service (the small "Verde Llama is
 * running" notification) plus a CPU wake lock stops Android from pausing it.
 *
 * Started once from the Ask tab; it keeps running until you tap "Stop" on the notification
 * or the phone restarts.
 */
class LlamaService : Service() {

    companion object {
        private const val TAG = "VERDE-LlamaService"
        private const val CHANNEL = "verde_llama"
        private const val NOTIFICATION_ID = 5001
        private const val ACTION_STOP = "com.verde.app.STOP_LLAMA"

        /** Starts the service (asks for notification permission first on Android 13+). */
        fun start(activity: Activity) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5001)
            }
            try {
                ContextCompat.startForegroundService(activity, Intent(activity, LlamaService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "could not start the service, using the Ask screen only", e)
                LlamaServer.start(activity.applicationContext)     // old behaviour as a fallback
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()

        // Keep the CPU awake (not the screen) so Llama can answer with the screen off
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Verde:Llama")
            .apply { setReferenceCounted(false); acquire() }

        LlamaServer.start(applicationContext)                      // port 5001 for the WhatsApp server

        // Load Llama now, so the first WhatsApp answer isn't slow
        scope.launch {
            val start = System.currentTimeMillis()
            val ok = LocalLlama.ask(applicationContext, "Hi") != null
            Log.d(TAG, "Llama warm-up ${if (ok) "done" else "failed"} in ${System.currentTimeMillis() - start} ms")
        }
        Log.d(TAG, "Verde Llama service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY                                        // Android restarts it if it's killed
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        Log.d(TAG, "Verde Llama service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun goForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Verde Llama", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while Verde's Llama answers WhatsApp messages"
            }
        )

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, AskActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LlamaService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Verde Llama is running")
            .setContentText("Answering WhatsApp with Llama 3.2 1B (ExecuTorch) on this phone")
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }
}
