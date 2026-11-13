package com.drain.scout

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        appendLine("Since baseline: ${-drain.deltaPct}% over ${formatDuration(drain.minutes)} (${"%.1f".format(drain.perHour)} %/h)")
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

