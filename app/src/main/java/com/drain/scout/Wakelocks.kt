package com.drain.scout

internal enum class Verdict { USUAL, UNUSUAL }

internal data class Judgement(
    val verdict: Verdict,
    val kind: String,
    val description: String,
    val reason: String
)

private class Rule(
    val kind: String,
    val description: String,
    val limitMin: Long,
    val matches: (String) -> Boolean
)

private val LOCATION_RX = Regex("(?i)location|Nlp|CollectionLib")

// Tag patterns and meanings come from Android's "Identify and optimize wake lock use cases" guide.
// The minute limits are DrainScout heuristics, not official Android thresholds.
private val RULES = listOf(
    Rule(
        "Hidden name",
        "The system hides this lock's name because it may contain personal data.",
        30L
    ) { it == "_UNKNOWN" },
    Rule(
        "Push message (FCM)",
        "Held while a push message is delivered to an app. It should last seconds, not minutes.",
        2L
    ) { it == "GOOGLE_C2DM" || it.startsWith("GCM_") },
    Rule(
        "Alarm",
        "Held by AlarmManager from the moment an alarm fires until the app's receiver finishes. It should last seconds.",
        2L
    ) { it.contains("*alarm*") },
    Rule(
        "Background job",
        "Held while JobScheduler or WorkManager runs a background task for the app. Normal for short tasks. A long hold means the task is heavy or stuck.",
        30L
    ) { it.contains("*job*") },
    Rule(
        "Location",
        "Held while Google Play services or an app collects location. Brief wakeups are expected, continuous holds are not.",
        15L
    ) { LOCATION_RX.containsMatchIn(it) },
    Rule(
        "Audio",
        "Held while audio plays or records so the CPU keeps feeding the sound. Normal while media is active.",
        180L
    ) { it.startsWith("Audio") || it == "MmapCapture" || it == "MmapPlayback" }
)

private val GENERIC = Rule(
    "App wake lock",
    "A custom lock named by the app's developer. DrainScout judges it by how long it has been held.",
    60L
) { true }

internal fun formatHeld(ms: Long): String =
    if (ms < 60_000L) "${ms / 1000}s" else formatDuration(ms / 60_000L)

internal fun judgeWakeLock(w: WakeLockEntry, wakefulness: String?): Judgement {
    val screenOff = wakefulness == "Asleep" || wakefulness == "Dozing"
    if (w.type != "PARTIAL") return judgeNonPartial(w.type, screenOff)

    val rule = RULES.firstOrNull { it.matches(w.tag) } ?: GENERIC
    val limitMin = if (rule === GENERIC) (if (screenOff) 10L else 60L) else rule.limitMin
    val held = w.heldMs

    val verdict: Verdict
    val reason: String
    if (held == null) {
        verdict = Verdict.USUAL
        reason = "This device did not report how long it has been held."
    } else if (held > limitMin * 60_000L) {
        verdict = Verdict.UNUSUAL
        reason = "Held for ${formatHeld(held)}, longer than the ${formatDuration(limitMin)} normally expected for this kind of lock. Google Play flags apps whose partial wake locks total 2 hours or more in 24 hours."
    } else {
        verdict = Verdict.USUAL
        reason = "Held for ${formatHeld(held)}, within the normal range for this kind of lock."
    }
    return Judgement(verdict, rule.kind, rule.description, reason)
}

private fun judgeNonPartial(type: String, screenOff: Boolean): Judgement = when (type) {
    "PROXIMITY_SCREEN_OFF" -> Judgement(
        Verdict.USUAL,
        "Proximity lock",
        "Turns the screen off when the phone is near your face, as during a call.",
        "Normal during calls."
    )
    "DOZE", "DRAW" -> Judgement(
        Verdict.USUAL,
        "System display lock",
        "Held by the system to finish drawing or to run ambient display.",
        "Normal system behaviour."
    )
    else -> if (screenOff) {
        Judgement(
            Verdict.UNUSUAL,
            "Screen lock",
            "Keeps the display on while an app is in use.",
            "Held while the screen is reported off, which should not happen."
        )
    } else {
        Judgement(
            Verdict.USUAL,
            "Screen lock",
            "Keeps the display on while an app is in use, such as video or navigation.",
            "The screen is on, so this is expected."
        )
    }
}

private fun kernelNote(name: String): String {
    val n = name.lowercase()
    return when {
        n == "powermanagerservice.wakelocks" ->
            "An aggregate. It is held whenever any app holds a partial wake lock, so check the app wake locks to see who."
        n.contains("alarmtimer") ->
            "Kernel timer that wakes the device when an alarm is due. Linked to AlarmManager activity."
        n.startsWith("wlan") ->
            "Wi-Fi traffic waking the device. Often an app syncing over Wi-Fi or chatty router traffic."
        n.contains("multipdp") || n.contains("dormancy") ->
            "Mobile data activity on the modem. Network traffic is waking the device."
        n.startsWith("mmc") ->
            "Storage or SD card controller activity."
        n.startsWith("smd") ->
            "Modem control channel activity, usually radio traffic."
        else ->
            "A kernel or driver wake lock. The name usually hints at the hardware involved."
    }
}

internal fun judgeKernelWakelock(k: KernelWakelock): Judgement {
    val unusual = k.durationMs / 60_000L >= 60L
    val reason = if (unusual) {
        "Held for ${k.duration} in total across ${k.count} activations since the last full charge. That is a lot."
    } else {
        "Held for ${k.duration} across ${k.count} activations since the last full charge."
    }
    return Judgement(
        if (unusual) Verdict.UNUSUAL else Verdict.USUAL,
        "Kernel wakelock",
        kernelNote(k.name),
        reason
    )
}
