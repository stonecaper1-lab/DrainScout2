package com.drain.scout

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

internal object DiagnosticEngine {

    fun hasShellPermission(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /**
     * Output goes back over Binder, which caps a transaction near 1 MB, so the big dumps are
     * filtered inside the shell before they are returned.
     */
    fun capture(context: Context): Snapshot {
        Shell.init(context)
        Shell.lastError = null

        val uidMap = Parsers.parseUidMap(Shell.run("cmd package list packages -U"))
        val battery = Shell.run("dumpsys battery")
        val power = Shell.run("dumpsys power")
        val alarm = Shell.run("dumpsys alarm | grep ' wakeups'")
        val idle = Shell.run("dumpsys deviceidle | grep -E 'mState=|mLightState='")
        val stats = Shell.run(
            "dumpsys batterystats --charged | sed -n -e '/Estimated power use/,/^\$/p' -e '/[Kk]ernel [Ww]ake lock/p'"
        )

        return Snapshot(
            capturedAt = System.currentTimeMillis(),
            battery = Parsers.parseBattery(battery),
            doze = Parsers.parseDoze(idle),
            consumers = Parsers.parseConsumers(stats, uidMap),
            wakeLocks = Parsers.parseWakeLocks(power, uidMap),
            alarms = Parsers.parseAlarms(alarm),
            kernelWakelocks = Parsers.parseKernelWakelocks(stats),
            wakefulness = Parsers.first(power, """mWakefulness=(\w+)""")
        )
    }
}
