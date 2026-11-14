package com.drain.scout

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

/** Persistent settings for continuous monitoring. */
internal object MonitorPrefs {
    private const val FILE = "drainscout_monitor"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = try { p(ctx).getBoolean("on", false) } catch (_: Exception) { false }
    fun setEnabled(ctx: Context, v: Boolean) { try { p(ctx).edit().putBoolean("on", v).apply() } catch (_: Exception) {} }

    fun intervalMin(ctx: Context): Int = try { p(ctx).getInt("min", 15) } catch (_: Exception) { 15 }
    fun setIntervalMin(ctx: Context, v: Int) { try { p(ctx).edit().putInt("min", v).apply() } catch (_: Exception) {} }
}

/**
 * Foreground service that captures on a timer so a whole discharge (100% to 0%) is recorded.
 * It sleeps between captures: each tick is an alarm that wakes the CPU briefly.
 */
class MonitorService : Service() {

    private val busy = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Shell.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            MonitorPrefs.setEnabled(this, false)
            cancelAlarm(this)
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!MonitorPrefs.enabled(this)) MonitorPrefs.setEnabled(this, true)
        goForeground(buildNotification("Monitoring battery drain…"))
        scheduleNext(this)
        tick()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away must not end monitoring.
        if (MonitorPrefs.enabled(this)) scheduleNext(this)
        super.onTaskRemoved(rootIntent)
    }

    private fun tick() {
        if (!busy.compareAndSet(false, true)) return
        val appContext = applicationContext
        val pm = getSystemService(PowerManager::class.java)
        val lock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DrainScout:monitor")
        try { lock?.acquire(90_000L) } catch (_: Exception) {}
        Thread {
            var text: String
            try {
                if (!DiagnosticEngine.hasShellPermission()) {
                    text = "Waiting for Shizuku. Start it so monitoring can continue."
                } else {
                    val snap = DiagnosticEngine.capture(appContext)
                    val entry = HistoryEntry.from(snap)
                    if (entry == null) {
                        text = "Last capture returned no data (${Shell.lastError ?: "unknown"})."
                    } else {
                        val saved = HistoryStore.addIfWorthKeeping(appContext, entry)
                        text = "${entry.level}% at ${formatTime(entry.time)}" +
                            (if (entry.plugged) " · charging" else "") +
                            (if (saved) "" else " · not saved while charging")
                    }
                }
            } catch (t: Throwable) {
                text = "Capture failed: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                busy.set(false)
                try { if (lock?.isHeld == true) lock.release() } catch (_: Exception) {}
            }
            try {
                getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(text))
            } catch (_: Exception) {}
        }.start()
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, FGS_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Battery monitor", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("DrainScout is monitoring")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        const val ACTION_TICK = "com.drain.scout.TICK"
        const val ACTION_STOP = "com.drain.scout.STOP"
        private const val CHANNEL_ID = "monitor"
        private const val NOTIF_ID = 4242
        private const val FGS_TYPE_SPECIAL_USE = 0x40000000

        private fun tickIntent(ctx: Context) =
            PendingIntent.getForegroundService(
                ctx, 1, Intent(ctx, MonitorService::class.java).setAction(ACTION_TICK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        fun scheduleNext(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val delay = MonitorPrefs.intervalMin(ctx) * 60_000L
            try {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + delay,
                    tickIntent(ctx)
                )
            } catch (_: Exception) {
            }
        }

        fun cancelAlarm(ctx: Context) {
            try { ctx.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(ctx)) } catch (_: Exception) {}
        }

        fun start(ctx: Context) {
            MonitorPrefs.setEnabled(ctx, true)
            ctx.startForegroundService(Intent(ctx, MonitorService::class.java).setAction(ACTION_TICK))
        }

        fun stop(ctx: Context) {
            MonitorPrefs.setEnabled(ctx, false)
            cancelAlarm(ctx)
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }
    }
}

/** Restarts monitoring after a reboot if the user left it on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && MonitorPrefs.enabled(context)) {
            try { MonitorService.start(context) } catch (_: Exception) {}
        }
    }
}
