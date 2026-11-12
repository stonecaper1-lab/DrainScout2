package com.drain.scout

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal enum class Severity { INFO, WATCH, ALERT }

internal data class Finding(val title: String, val detail: String, val severity: Severity)
internal data class BatteryInfo(val level: Int, val tempC: Float, val status: String, val plugged: Boolean)
internal data class DozeInfo(val deep: String, val light: String)
internal data class Consumer(val label: String, val mah: Float)
internal data class WakeLockEntry(val owner: String, val tag: String, val type: String, val heldMs: Long?)
internal data class AlarmEntry(val owner: String, val wakeups: Int)
internal data class KernelWakelock(val name: String, val duration: String, val count: Int, val durationMs: Long)
internal data class Drain(val deltaPct: Int, val minutes: Long, val perHour: Float)

internal data class Snapshot(
    val capturedAt: Long,
    val battery: BatteryInfo?,
    val doze: DozeInfo?,
    val consumers: List<Consumer>,
    val wakeLocks: List<WakeLockEntry>,
    val alarms: List<AlarmEntry>,
    val kernelWakelocks: List<KernelWakelock>,
    val wakefulness: String?
)

internal object DiagnosticEngine {

    fun hasShellPermission(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun capture(): Snapshot {
        lastShellError = null
        val uidMap = loadUidMap()
        val battery = shell("dumpsys", "battery")
        val power = shell("dumpsys", "power")
        val alarm = shell("dumpsys", "alarm")
        val idle = shell("dumpsys", "deviceidle")
        val stats = shell("dumpsys", "batterystats", "--charged")

        return Snapshot(
            capturedAt = System.currentTimeMillis(),
            battery = parseBattery(battery),
            doze = parseDoze(idle),
            consumers = parseConsumers(stats, uidMap),
            wakeLocks = parseWakeLocks(power, uidMap),
            alarms = parseAlarms(alarm),
            kernelWakelocks = parseKernelWakelocks(stats),
            wakefulness = first(power, """mWakefulness=(\w+)""")
        )
    }

    // ------------------------------------------------------------ parsing

    private fun first(text: String, pattern: String): String? =
        Regex(pattern, RegexOption.MULTILINE).find(text)?.groupValues?.getOrNull(1)

    private fun parseBattery(t: String): BatteryInfo? {
        val level = first(t, """^\s*level:\s*(\d+)""")?.toIntOrNull() ?: return null
        val temp = (first(t, """^\s*temperature:\s*(-?\d+)""")?.toIntOrNull() ?: 0) / 10f
        val status = when (first(t, """^\s*status:\s*(\d+)""")?.toIntOrNull()) {
            2 -> "Charging"
            3 -> "Discharging"
            4 -> "Not charging"
            5 -> "Full"
            else -> "Unknown"
        }
        val plugged = Regex("""(AC|USB|Wireless|Dock) powered:\s*true""").containsMatchIn(t)
        return BatteryInfo(level, temp, status, plugged)
    }

    private fun parseDoze(t: String): DozeInfo? {
        val deep = first(t, """mState=(\w+)""")
        val light = first(t, """mLightState=(\w+)""")
        if (deep == null && light == null) return null
        return DozeInfo(deep ?: "n/a", light ?: "n/a")
    }

    private fun loadUidMap(): Map<Int, String> {
        val out = shell("cmd", "package", "list", "packages", "-U")
        val map = HashMap<Int, String>()
        Regex("""package:(\S+)\s+uid:(\d+)""").findAll(out).forEach { m ->
            val uid = m.groupValues[2].toIntOrNull() ?: return@forEach
            if (!map.containsKey(uid)) map[uid] = m.groupValues[1]
        }
        return map
    }

    private fun resolveOwner(token: String, uidMap: Map<Int, String>): String {
        val m = Regex("""^u(\d+)a(\d+)$""").find(token)
        val uid: Int = when {
            m != null -> {
                val user = m.groupValues[1].toIntOrNull() ?: return token
                val app = m.groupValues[2].toIntOrNull() ?: return token
                user * 100000 + 10000 + app
            }
            token.isNotEmpty() && token.all { it.isDigit() } -> token.toIntOrNull() ?: return token
            else -> return token
        }
        return uidMap[uid] ?: uidMap[uid % 100000] ?: token
    }

    private fun parseConsumers(stats: String, uidMap: Map<Int, String>): List<Consumer> {
        val lines = stats.lines()
        val start = lines.indexOfFirst { it.contains("Estimated power use (mAh)") }
        if (start < 0) return emptyList()
        val rx = Regex("""^\s*([A-Za-z][^:]*?):\s*([\d.]+)""")
        val out = ArrayList<Consumer>()
        for (i in start + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) break
            val m = rx.find(line) ?: continue
            val name = m.groupValues[1].trim()
            if (name.startsWith("Capacity")) continue
            val mah = m.groupValues[2].toFloatOrNull() ?: continue
            val label = if (name.startsWith("Uid ")) {
                resolveOwner(name.removePrefix("Uid ").trim(), uidMap)
            } else {
                name
            }
            out.add(Consumer(label, mah))
        }
        return out.sortedByDescending { it.mah }.take(10)
    }

    /** Parses durations such as "1h2m3s4ms" or "5m 3s 120ms". Returns null if nothing matched. */
    private fun parseDurationMs(text: String): Long? {
        var total = 0L
        var found = false
        Regex("""(\d+)(ms|d|h|m|s)""").findAll(text).forEach { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return@forEach
            total += when (m.groupValues[2]) {
                "d" -> n * 86_400_000L
                "h" -> n * 3_600_000L
                "m" -> n * 60_000L
                "s" -> n * 1_000L
                else -> n
            }
            found = true
        }
        return if (found) total else null
    }

    /**
     * Example line: PARTIAL_WAKE_LOCK 'tag' ACQ=-1m5s (uid=1000 pid=900 ws=WorkSource{10123})
     * When the system holds a lock on behalf of an app, the WorkSource uid is the real owner.
     */
    private fun parseWakeLocks(power: String, uidMap: Map<Int, String>): List<WakeLockEntry> {
        val head = Regex("""(\w*WAKE_LOCK)\s+'([^']*)'(.*)""")
        val uidRx = Regex("""uid=(\d+)""")
        val wsRx = Regex("""ws=WorkSource\{(\d+)""")
        val acqRx = Regex("""ACQ=(\S+)""")
        return head.findAll(power)
            .map { m ->
                val rest = m.groupValues[3]
                val uid = uidRx.find(rest)?.groupValues?.get(1)
                val ws = wsRx.find(rest)?.groupValues?.get(1)
                val acq = acqRx.find(rest)?.groupValues?.get(1)
                WakeLockEntry(
                    owner = resolveOwner(ws ?: uid ?: "unknown", uidMap),
                    tag = m.groupValues[2],
                    type = m.groupValues[1].removeSuffix("_WAKE_LOCK").ifEmpty { "WAKE" },
                    heldMs = acq?.let { parseDurationMs(it) }
                )
            }
            .distinct()
            .take(40)
            .toList()
    }

    private fun parseAlarms(alarm: String): List<AlarmEntry> {
        val rx = Regex(
            """^\s*[A-Za-z0-9_]+:([A-Za-z0-9_.]+)\s+\+[^,\n]*,\s*(\d+)\s+wakeups""",
            RegexOption.MULTILINE
        )
        val totals = HashMap<String, Int>()
        rx.findAll(alarm).forEach { m ->
            val count = m.groupValues[2].toIntOrNull() ?: return@forEach
            totals[m.groupValues[1]] = (totals[m.groupValues[1]] ?: 0) + count
        }
        return totals.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { AlarmEntry(it.key, it.value) }
    }

    private fun parseKernelWakelocks(stats: String): List<KernelWakelock> =
        Regex("""Kernel [Ww]ake lock (.+?): (.+?) \((\d+) times\)""")
            .findAll(stats)
            .mapNotNull { m ->
                val count = m.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                KernelWakelock(
                    name = m.groupValues[1],
                    duration = m.groupValues[2],
                    count = count,
                    durationMs = parseDurationMs(m.groupValues[2]) ?: 0L
                )
            }
            .sortedByDescending { it.durationMs }
            .take(12)
            .toList()

    // ------------------------------------------------------------ shell

    /** Why the last shell command failed. Shown in the UI so failures are never silent. */
    @Volatile
    var lastShellError: String? = null

    /**
     * Shizuku.newProcess is private since API 13.1, so it is called reflectively.
     * Stdout is drained on its own thread so large dumpsys output cannot fill the pipe.
     * Returns an empty string on any failure and records the reason in lastShellError.
     */
    private fun shell(vararg args: String): String = try {
        val method = Shizuku::class.java.declaredMethods
            .firstOrNull { it.name == "newProcess" && it.parameterTypes.size == 3 }
            ?: error("Shizuku.newProcess was not found in this Shizuku API version")
        method.isAccessible = true
        val process = method.invoke(null, arrayOf(*args), null, null) as Process

        var output = ""
        val reader = Thread {
            output = try {
                process.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Throwable) {
                ""
            }
        }
        reader.start()

        val completed = process.waitFor(30, TimeUnit.SECONDS)
        if (!completed) process.destroy()
        reader.join(3_000)
        output
    } catch (t: Throwable) {
        val root = t.cause ?: t
        lastShellError = "${root.javaClass.simpleName}: ${root.message}"
        ""
    }
}

// ---------------------------------------------------------------- analysis

internal fun drainSince(refLevel: Int, refTime: Long, level: Int, now: Long): Drain? {
    val minutes = (now - refTime) / 60_000L
    if (minutes < 1L) return null
    val delta = refLevel - level
    return Drain(delta, minutes, delta * 60f / minutes)
}

internal fun formatDuration(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

internal fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))

internal fun buildFindings(s: Snapshot, drain: Drain?): List<Finding> = buildList {
    val onBattery = s.battery?.plugged != true

    if (drain != null && onBattery && drain.minutes >= 10L && drain.deltaPct > 0) {
        val rate = "%.1f".format(drain.perHour)
        when {
            drain.perHour >= 4f -> add(
                Finding(
                    "High drain rate",
                    "$rate %/h over ${formatDuration(drain.minutes)}. Check the wakelock and alarm tabs for repeat offenders.",
                    Severity.ALERT
                )
            )
            drain.perHour >= 2f -> add(
                Finding(
                    "Moderate drain rate",
                    "$rate %/h over ${formatDuration(drain.minutes)}. Normal with the screen on, worth a look if the phone was idle.",
                    Severity.WATCH
                )
            )
            else -> add(
                Finding(
                    "Drain rate looks low",
                    "$rate %/h over ${formatDuration(drain.minutes)}.",
                    Severity.INFO
                )
            )
        }
    }

    s.consumers.firstOrNull()?.let {
        add(
            Finding(
                "${it.label} leads estimated usage",
                "%.1f mAh since the last full charge, according to batterystats.".format(it.mah),
                Severity.INFO
            )
        )
    }

    val judged = s.wakeLocks.map { it to judgeWakeLock(it, s.wakefulness) }
    val unusual = judged.filter { it.second.verdict == Verdict.UNUSUAL }
    if (unusual.isNotEmpty()) {
        val top = unusual.take(3).joinToString { "${it.first.owner} (${it.second.kind})" }
        add(
            Finding(
                "${unusual.size} unusual wake lock(s)",
                "Worst offenders: $top. The Wakelocks tab explains each one.",
                Severity.ALERT
            )
        )
    } else if (judged.isNotEmpty()) {
        add(
            Finding(
                "Wake locks look normal",
                "${judged.size} held right now, all within the usual range for their kind.",
                Severity.INFO
            )
        )
    }

    val kernelUnusual = s.kernelWakelocks.filter { judgeKernelWakelock(it).verdict == Verdict.UNUSUAL }
    if (kernelUnusual.isNotEmpty()) {
        add(
            Finding(
                "${kernelUnusual.size} heavy kernel wakelock(s)",
                "Led by ${kernelUnusual.first().name}. Totals are since the last full charge.",
                Severity.WATCH
            )
        )
    }

    s.alarms.firstOrNull()?.let {
        if (it.wakeups >= 100) {
            add(
                Finding(
                    "${it.owner} wakes the device often",
                    "${it.wakeups} alarm wakeups recorded.",
                    Severity.WATCH
                )
            )
        }
    }

    s.doze?.let {
        val deepOn = it.deep == "IDLE" || it.deep == "IDLE_MAINTENANCE"
        add(
            Finding(
                if (deepOn) "Deep Doze is active" else "Not in deep Doze right now",
                if (deepOn) "The device is idling as intended." else "Expected shortly after use or while charging. Capture again after a long screen-off period.",
                Severity.INFO
            )
        )
    }

    if (isEmpty()) {
        add(Finding("Not enough data", "Nothing parsed from this device's output yet. Try Capture again.", Severity.INFO))
    }
}

internal fun buildReport(s: Snapshot, drain: Drain?): String = buildString {
    appendLine("DrainScout report")
    appendLine("Captured: ${formatTime(s.capturedAt)}")
    s.battery?.let {
        appendLine("Battery: ${it.level}% (${it.status}), ${"%.1f".format(it.tempC)} C")
    }
    appendLine("Screen state: ${s.wakefulness ?: "unknown"}")
    if (drain != null) {
        appendLine("Since reference: ${-drain.deltaPct}% over ${formatDuration(drain.minutes)} (${"%.1f".format(drain.perHour)} %/h)")
    }
    appendLine("Note: triage evidence, not final causation.")
    appendLine()
    appendLine("== Findings ==")
    buildFindings(s, drain).forEach { appendLine("- ${it.title}: ${it.detail}") }
    appendLine()
    appendLine("== Top consumers (mAh) ==")
    if (s.consumers.isEmpty()) appendLine("none parsed") else s.consumers.forEach { appendLine("${"%.1f".format(it.mah)}  ${it.label}") }
    appendLine()
    appendLine("== Wake locks held now ==")
    if (s.wakeLocks.isEmpty()) {
        appendLine("none parsed")
    } else {
        s.wakeLocks.forEach { w ->
            val j = judgeWakeLock(w, s.wakefulness)
            val held = w.heldMs?.let { formatHeld(it) } ?: "unknown"
            appendLine("[${j.verdict}] ${w.owner}  ${w.type}  '${w.tag}'  held $held")
            appendLine("    ${j.kind}: ${j.reason}")
        }
    }
    appendLine()
    appendLine("== Kernel wakelocks ==")
    if (s.kernelWakelocks.isEmpty()) {
        appendLine("none parsed")
    } else {
        s.kernelWakelocks.forEach { k ->
            val j = judgeKernelWakelock(k)
            appendLine("[${j.verdict}] ${k.count}x  ${k.duration}  ${k.name}")
        }
    }
    appendLine()
    appendLine("== Alarm wakeups ==")
    if (s.alarms.isEmpty()) appendLine("none parsed") else s.alarms.forEach { appendLine("${it.wakeups}  ${it.owner}") }
    appendLine()
    appendLine("== Doze ==")
    appendLine(s.doze?.let { "deep=${it.deep} light=${it.light}" } ?: "none parsed")
}
