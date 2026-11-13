package com.drain.scout

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


internal object Parsers {

    fun first(text: String, pattern: String): String? =
        Regex(pattern, RegexOption.MULTILINE).find(text)?.groupValues?.getOrNull(1)

    fun parseBattery(t: String): BatteryInfo? {
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

    fun parseDoze(t: String): DozeInfo? {
        val deep = first(t, """mState=(\w+)""")
        val light = first(t, """mLightState=(\w+)""")
        if (deep == null && light == null) return null
        return DozeInfo(deep ?: "n/a", light ?: "n/a")
    }

    fun parseUidMap(text: String): Map<Int, String> {
        val map = HashMap<Int, String>()
        Regex("""package:(\S+)\s+uid:(\d+)""").findAll(text).forEach { m ->
            val uid = m.groupValues[2].toIntOrNull() ?: return@forEach
            if (!map.containsKey(uid)) map[uid] = m.groupValues[1]
        }
        return map
    }

    fun resolveOwner(token: String, uidMap: Map<Int, String>): String {
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

    fun parseConsumers(stats: String, uidMap: Map<Int, String>): List<Consumer> {
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
    fun parseDurationMs(text: String): Long? {
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
    fun parseWakeLocks(power: String, uidMap: Map<Int, String>): List<WakeLockEntry> {
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

    fun parseAlarms(alarm: String): List<AlarmEntry> {
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

    fun parseKernelWakelocks(stats: String): List<KernelWakelock> =
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

}
