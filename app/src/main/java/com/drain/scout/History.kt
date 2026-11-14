package com.drain.scout

import android.content.Context

internal data class HistWake(val owner: String, val tag: String, val heldMs: Long, val unusual: Boolean)

internal data class HistoryEntry(
    val time: Long,
    val level: Int,
    val plugged: Boolean,
    val screen: String,
    val tempC: Float,
    val wakeTotal: Int,
    val wakeUnusual: Int,
    val wakes: List<HistWake>,
    val consumers: List<Consumer>,
    val alarms: List<AlarmEntry>,
    val kernel: List<KernelWakelock>
) {
    companion object {
        /** Compact summary of a capture. Unusual wake locks are listed first and lists are capped. */
        fun from(s: Snapshot): HistoryEntry? {
            val b = s.battery ?: return null
            val judged = s.wakeLocks.map { it to judgeWakeLock(it, s.wakefulness) }
            val wakes = judged
                .sortedByDescending { it.second.verdict == Verdict.UNUSUAL }
                .take(15)
                .map { (w, j) -> HistWake(w.owner, w.tag, w.heldMs ?: -1L, j.verdict == Verdict.UNUSUAL) }
            return HistoryEntry(
                time = s.capturedAt,
                level = b.level,
                plugged = b.plugged,
                screen = s.wakefulness ?: "Unknown",
                tempC = b.tempC,
                wakeTotal = judged.size,
                wakeUnusual = judged.count { it.second.verdict == Verdict.UNUSUAL },
                wakes = wakes,
                consumers = s.consumers.take(6),
                alarms = s.alarms.take(6),
                kernel = s.kernelWakelocks.take(5)
            )
        }
    }
}

/** Line-based text format. Fields are tab separated, list items '|', sub-fields '~'. */
internal object HistoryCodec {
    private fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '\t' -> append("\\t")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '|' -> append("\\p")
            '~' -> append("\\s")
            else -> append(c)
        }
    }

    private fun unesc(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> append('\\')
                    't' -> append('\t')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    'p' -> append('|')
                    's' -> append('~')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    private fun items(s: String): List<List<String>> =
        if (s.isEmpty()) emptyList() else s.split('|').map { item -> item.split('~').map { unesc(it) } }

    private fun join(rows: List<List<String>>): String =
        rows.joinToString("|") { row -> row.joinToString("~") { esc(it) } }

    fun encode(e: HistoryEntry): String = listOf(
        e.time.toString(),
        e.level.toString(),
        if (e.plugged) "1" else "0",
        esc(e.screen),
        e.tempC.toString(),
        e.wakeTotal.toString(),
        e.wakeUnusual.toString(),
        join(e.wakes.map { listOf(it.owner, it.tag, it.heldMs.toString(), if (it.unusual) "1" else "0") }),
        join(e.consumers.map { listOf(it.label, it.mah.toString()) }),
        join(e.alarms.map { listOf(it.owner, it.wakeups.toString()) }),
        join(e.kernel.map { listOf(it.name, it.duration, it.count.toString(), it.durationMs.toString()) })
    ).joinToString("\t")

    fun decode(line: String): HistoryEntry? = try {
        val f = line.split('\t')
        if (f.size < 11) {
            null
        } else {
            HistoryEntry(
                time = f[0].toLong(),
                level = f[1].toInt(),
                plugged = f[2] == "1",
                screen = unesc(f[3]),
                tempC = f[4].toFloat(),
                wakeTotal = f[5].toInt(),
                wakeUnusual = f[6].toInt(),
                wakes = items(f[7]).map { HistWake(it[0], it[1], it[2].toLong(), it[3] == "1") },
                consumers = items(f[8]).map { Consumer(it[0], it[1].toFloat()) },
                alarms = items(f[9]).map { AlarmEntry(it[0], it[1].toInt()) },
                kernel = items(f[10]).map { KernelWakelock(it[0], it[1], it[2].toInt(), it[3].toLong()) }
            )
        }
    } catch (_: Exception) {
        null
    }
}

internal object HistoryStore {
    private const val FILE = "drainscout_history"
    private const val KEY = "entries"
    const val MAX = 300

    /** Newest first. */
    fun load(ctx: Context): List<HistoryEntry> {
        val raw = try {
            ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "")
        } catch (_: Exception) {
            ""
        } ?: ""
        return raw.split('\n').filter { it.isNotBlank() }
            .mapNotNull { HistoryCodec.decode(it) }
            .sortedByDescending { it.time }
    }

    fun add(ctx: Context, entry: HistoryEntry) {
        val merged = (listOf(entry) + load(ctx).filter { it.time != entry.time })
            .sortedByDescending { it.time }
            .take(MAX)
        save(ctx, merged)
    }

    /** While charging, keep only the first sample of a charge so the history stays about draining. */
    fun addIfWorthKeeping(ctx: Context, entry: HistoryEntry): Boolean {
        val last = load(ctx).firstOrNull()
        if (entry.plugged && last != null && last.plugged) return false
        add(ctx, entry)
        return true
    }

    fun clear(ctx: Context) = save(ctx, emptyList())

    private fun save(ctx: Context, entries: List<HistoryEntry>) {
        try {
            ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(KEY, entries.joinToString("\n") { HistoryCodec.encode(it) })
                .apply()
        } catch (_: Exception) {
        }
    }
}

/** The discharge in progress: unplugged samples back to where the battery last went up or was charging. */
internal data class DischargeRun(
    val startLevel: Int,
    val startTime: Long,
    val endLevel: Int,
    val endTime: Long,
    val samples: Int
) {
    val minutes: Long get() = (endTime - startTime) / 60_000L
    val perHour: Float get() = if (minutes < 1L) 0f else (startLevel - endLevel) * 60f / minutes
    /** Hours until empty at the average rate, or null if the rate is not meaningful yet. */
    val hoursLeft: Float? get() = if (perHour >= 0.1f) endLevel / perHour else null
}

internal fun currentRun(newestFirst: List<HistoryEntry>): DischargeRun? {
    val latest = newestFirst.firstOrNull() ?: return null
    if (latest.plugged) return null
    var oldest = latest
    var count = 1
    for (e in newestFirst.drop(1)) {
        if (e.plugged || e.level < oldest.level) break
        oldest = e
        count++
    }
    if (count < 2 || latest.time <= oldest.time) return null
    return DischargeRun(oldest.level, oldest.time, latest.level, latest.time, count)
}
