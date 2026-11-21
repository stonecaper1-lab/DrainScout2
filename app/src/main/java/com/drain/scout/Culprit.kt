package com.drain.scout

internal enum class Confidence { HIGH, MEDIUM, LOW }

internal data class Suspect(
    val title: String,
    val evidence: String,
    val action: String,
    val confidence: Confidence
)

internal enum class SleepState { UNKNOWN, SLEEPS, PARTLY, NEVER }

internal data class SleepVerdict(
    val state: SleepState,
    val headline: String,
    val detail: String,
    val awakePct: Int?,
    val offMs: Long,
    val awakeMs: Long,
    val basis: String
)

internal data class SleepWindow(val offMs: Long, val awakeMs: Long)

private const val MIN_WINDOW_MS = 10 * 60_000L
private const val MIN_ANY_MS = 5 * 60_000L

/** Screen-off time and CPU-awake time between two saved captures, or null if it cannot be worked out. */
internal fun sleepBetween(newer: HistoryEntry, older: HistoryEntry): SleepWindow? {
    if (newer.offRealMs < 0 || older.offRealMs < 0 || newer.offUpMs < 0 || older.offUpMs < 0) return null
    val off = newer.offRealMs - older.offRealMs
    val up = newer.offUpMs - older.offUpMs
    if (off <= 0 || up < 0) return null // counters were reset by a charge
    return SleepWindow(off, minOf(up, off))
}

internal fun judgeSleep(s: Snapshot, history: List<HistoryEntry>): SleepVerdict {
    var off = 0L
    var awake = 0L
    var basis = ""

    val idx = history.indexOfFirst { it.time == s.capturedAt }
    if (idx >= 0) {
        val newest = history[idx]
        for (older in history.drop(idx + 1)) {
            val w = sleepBetween(newest, older) ?: break
            if (w.offMs >= MIN_WINDOW_MS) {
                off = w.offMs
                awake = w.awakeMs
                basis = "since the capture at ${formatTime(older.time)}"
                break
            }
        }
    }
    if (off == 0L) {
        s.sleep?.let {
            off = it.offRealMs
            awake = minOf(it.offUpMs, it.offRealMs)
            basis = "since the last full charge"
        }
    }

    if (off < MIN_ANY_MS) {
        return SleepVerdict(
            SleepState.UNKNOWN,
            "Not enough screen-off time yet",
            "Lock the phone for 30 minutes or more, then capture again. The monitor on the Timeline tab can do this for you.",
            null, off, awake, basis
        )
    }
    val pct = (awake * 100 / off).toInt()
    val numbers = "In ${formatDuration(off / 60_000L)} of screen-off time the CPU was awake for ${formatDuration(awake / 60_000L)} ($pct%)."
    return when {
        pct >= 90 -> SleepVerdict(
            SleepState.NEVER,
            "Your phone never sleeps with the screen off",
            "$numbers A healthy phone is awake for well under 20% of that time, so something is holding it awake.",
            pct, off, awake, basis
        )
        pct >= 40 -> SleepVerdict(
            SleepState.PARTLY,
            "Your phone only sleeps part of the time",
            "$numbers Healthy is well under 20%.",
            pct, off, awake, basis
        )
        else -> SleepVerdict(
            SleepState.SLEEPS,
            "Your phone sleeps properly",
            "$numbers That is in the normal range.",
            pct, off, awake, basis
        )
    }
}

private val COMPONENT_LABELS = setOf(
    "cpu", "screen", "idle", "wifi", "bluetooth", "cell standby", "phone calls", "camera", "flashlight",
    "gnss", "sensors", "memory", "audio", "video", "wake lock", "ambient display", "mobile radio",
    "system services", "unaccounted", "over-counted", "mobile network", "wi-fi"
)

/** True for hardware or system components in batterystats, as opposed to apps. */
internal fun isComponentLabel(label: String): Boolean = label.trim().lowercase() in COMPONENT_LABELS

private val KERNEL_THREAD_PREFIXES = listOf(
    "kworker", "irq/", "rcu", "ksoftirqd", "sugov", "migration", "kswapd", "mmc", "kthreadd", "ueventd"
)

private fun isKernelThread(name: String) = KERNEL_THREAD_PREFIXES.any { name.startsWith(it) }

private fun isSystemProcess(name: String) =
    !name.contains('.') || name.startsWith("android.") || name.startsWith("com.android.") ||
        name == "system_server" || name.startsWith("com.qualcomm") || name.startsWith("vendor.")

internal fun findSuspects(s: Snapshot, sleep: SleepVerdict): List<Suspect> {
    val out = ArrayList<Suspect>()
    val blocked = sleep.state == SleepState.NEVER || sleep.state == SleepState.PARTLY
    val screenOff = s.wakefulness == "Asleep" || s.wakefulness == "Dozing"

    // 1. The system's doze service holding the CPU awake
    val doze = s.wakeLocks.firstOrNull { it.type == "DOZE" && (it.heldMs ?: 0L) >= MIN_ANY_MS }
    if (doze != null && s.wakefulness == "Dozing") {
        val only = s.wakeLocks.none { it.type == "PARTIAL" }
        out.add(
            Suspect(
                "The system's doze service ('${doze.tag}')",
                "It has held the CPU for ${formatHeld(doze.heldMs ?: 0L)} while the screen is off" +
                    (if (only) ", and it is the only wake lock held." else "."),
                "Turn off Always-on display and lift-to-check or pick-up gestures in Settings, then capture again after 30 minutes. If it still holds the lock, this is a ROM-level problem: share the Report with the ROM's maintainers.",
                if (sleep.state == SleepState.NEVER) Confidence.HIGH else Confidence.MEDIUM
            )
        )
    }

    // 2. Unusual app wake locks
    s.wakeLocks.filter { it.type == "PARTIAL" }
        .map { it to judgeWakeLock(it, s.wakefulness) }
        .filter { it.second.verdict == Verdict.UNUSUAL }
        .take(3)
        .forEach { (w, j) ->
            val app = w.owner.contains('.')
            out.add(
                Suspect(
                    "${w.owner} holds '${w.tag}'",
                    j.reason,
                    if (app) "Open ${w.owner} in App info, tap Battery and restrict background use, or uninstall it if you do not need it. Then capture again."
                    else "A system lock. Restart the phone; if it comes back, share the Report with your ROM's maintainers.",
                    if (blocked || sleep.state == SleepState.UNKNOWN) Confidence.HIGH else Confidence.MEDIUM
                )
            )
        }

    // 3. Heavy kernel wakelocks
    s.kernelWakelocks.filter { judgeKernelWakelock(it).verdict == Verdict.UNUSUAL }.take(2).forEach { k ->
        out.add(
            Suspect(
                "Kernel wakelock ${k.name}",
                "Held for ${k.duration} across ${k.count} activations since the last charge.",
                kernelNote(k.name),
                Confidence.MEDIUM
            )
        )
    }

    // 4. Busy processes
    s.processes
        .filter { it.percent >= 5f && !isKernelThread(it.name) && !it.name.startsWith("com.drain.scout") }
        .take(3)
        .forEach { p ->
            val system = isSystemProcess(p.name)
            val conf = when {
                !screenOff -> Confidence.LOW
                system -> Confidence.LOW
                else -> Confidence.MEDIUM
            }
            out.add(
                Suspect(
                    "${p.name} is using ${"%.0f".format(p.percent)}% CPU",
                    "One of the busiest processes in the last few minutes" +
                        (if (screenOff) "." else ", but the screen was on, so this includes your own use. Capture with the screen off for a fairer reading."),
                    if (system) "A system component. It usually reflects what apps or the display are asking of it."
                    else "Check ${p.name}'s battery setting, or force stop it and capture again.",
                    conf
                )
            )
        }

    // 5. Alarm rate since boot
    if (s.uptimeMs > 3_600_000L) {
        s.alarms.map { it to it.wakeups * 3_600_000f / s.uptimeMs }
            .filter { it.second >= 20f }
            .take(2)
            .forEach { (a, perH) ->
                out.add(
                    Suspect(
                        "${a.owner} wakes the phone about ${"%.0f".format(perH)} times an hour",
                        "${a.wakeups} alarm wakeups since the phone started.",
                        "Check how often this app syncs or refreshes in its settings.",
                        Confidence.MEDIUM
                    )
                )
            }
    }

    // 6. Top app by estimated power
    s.consumers.firstOrNull { !isComponentLabel(it.label) && it.label.contains('.') && it.mah >= 50f }?.let {
        out.add(
            Suspect(
                "${it.label} leads app power use",
                "${"%.0f".format(it.mah)} mAh since the last full charge, according to batterystats.",
                "Open its App info > Battery, and check whether it needs to run in the background.",
                Confidence.LOW
            )
        )
    }

    return out.sortedBy { it.confidence.ordinal }
}
