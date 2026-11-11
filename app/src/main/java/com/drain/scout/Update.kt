package com.drain.scout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object UpdatePrefs {
    private fun prefs(c: Context) = c.getSharedPreferences("drainscout", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = prefs(c).getBoolean("update_alerts", true)
    fun setEnabled(c: Context, value: Boolean) {
        prefs(c).edit().putBoolean("update_alerts", value).apply()
    }

    fun askedPermission(c: Context): Boolean = prefs(c).getBoolean("asked_notif", false)
    fun setAskedPermission(c: Context) {
        prefs(c).edit().putBoolean("asked_notif", true).apply()
    }

    fun lastNotified(c: Context): String? = prefs(c).getString("last_notified_tag", null)
    fun setLastNotified(c: Context, tag: String) {
        prefs(c).edit().putString("last_notified_tag", tag).apply()
    }
}

internal object UpdateScheduler {
    private const val JOB_ID = 4242
    private const val PERIOD_MS = 6L * 60L * 60L * 1000L

    fun ensure(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
    }

    fun sync(context: Context, enabled: Boolean) {
        if (enabled) ensure(context) else cancel(context)
    }
}

internal object UpdateChecker {
    private const val API = "https://api.github.com/repos/stonecaper1-lab/DrainScout2/releases/latest"
    private const val FALLBACK_URL = "https://github.com/stonecaper1-lab/DrainScout2/releases"
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 1001

    data class Latest(val tag: String, val url: String)

    /** Returns null when there is no release yet or GitHub answers with anything but 200. */
    fun fetchLatest(): Latest? {
        val conn = URL(API).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "DrainScout")
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name")
            if (tag.isEmpty()) return null
            return Latest(tag, json.optString("html_url", FALLBACK_URL))
        } finally {
            conn.disconnect()
        }
    }

    fun isNewer(tag: String, current: String): Boolean {
        val a = numbers(tag)
        val b = numbers(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        Regex("""\d+""").findAll(version).mapNotNull { it.value.toIntOrNull() }.toList()

    fun notifyUpdate(context: Context, latest: Latest) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (!manager.areNotificationsEnabled()) return

        val channel = NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Tells you when a new DrainScout release is published"
        manager.createNotificationChannel(channel)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse(latest.url)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_update)
            .setContentTitle("DrainScout ${latest.tag} is available")
            .setContentText("Tap to open the release page and download the APK.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}

internal class UpdateJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        Thread {
            var retry = false
            try {
                if (UpdatePrefs.enabled(this)) {
                    val latest = UpdateChecker.fetchLatest()
                    if (latest != null) {
                        val current = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                        if (UpdateChecker.isNewer(latest.tag, current) &&
                            UpdatePrefs.lastNotified(this) != latest.tag
                        ) {
                            UpdateChecker.notifyUpdate(this, latest)
                            UpdatePrefs.setLastNotified(this, latest.tag)
                        }
                    }
                }
            } catch (_: Throwable) {
                retry = true
            }
            jobFinished(params, retry)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
