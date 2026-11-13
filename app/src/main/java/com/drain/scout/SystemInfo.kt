package com.drain.scout

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.view.Display
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class InfoRow(val label: String, val value: String)
internal data class InfoSection(val title: String, val rows: List<InfoRow>)

private inline fun <T> safe(block: () -> T): T? = try {
    block()
} catch (_: Throwable) {
    null
}

private fun gb(bytes: Long): String = "%.1f GB".format(bytes / 1_073_741_824.0)

private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

private fun formatUptime(ms: Long): String {
    val totalMin = ms / 60_000L
    val d = totalMin / 1_440L
    val h = (totalMin % 1_440L) / 60L
    val m = totalMin % 60L
    return if (d > 0) "${d}d ${h}h ${m}m" else "${h}h ${m}m"
}

internal fun collectSystemInfo(context: Context): List<InfoSection> = listOfNotNull(
    safe { deviceSection() },
    safe { androidSection() },
    safe { processorSection() },
    safe { memorySection(context) },
    safe { displaySection(context) },
    safe { batterySection(context) },
    safe { powerSection(context) },
    safe { shizukuSection() }
)

private fun deviceSection(): InfoSection {
    val rows = ArrayList<InfoRow>()
    rows.add(InfoRow("Model", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"))
    rows.add(InfoRow("Codename", Build.DEVICE))
    rows.add(InfoRow("Brand", Build.BRAND))
    rows.add(InfoRow("Board", Build.BOARD))
    rows.add(InfoRow("Hardware", Build.HARDWARE))
    if (Build.VERSION.SDK_INT >= 31) {
        val soc = listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL)
            .filter { it.isNotBlank() && it != Build.UNKNOWN }
            .joinToString(" ")
        if (soc.isNotEmpty()) rows.add(InfoRow("SoC", soc))
    }
    return InfoSection("Device", rows)
}

private fun androidSection(): InfoSection {
    val rows = ArrayList<InfoRow>()
    rows.add(InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"))
    rows.add(InfoRow("Security patch", Build.VERSION.SECURITY_PATCH))
    rows.add(InfoRow("Build", Build.DISPLAY))
    rows.add(InfoRow("Build type", "${Build.TYPE} / ${Build.TAGS}"))
    rows.add(InfoRow("Kernel", System.getProperty("os.version") ?: "unknown"))
    rows.add(InfoRow("Bootloader", Build.BOOTLOADER))
    val baseband = safe { Build.getRadioVersion() }.orEmpty()
    if (baseband.isNotBlank()) rows.add(InfoRow("Baseband", baseband))
    return InfoSection("Android", rows)
}

private fun cpuClocks(cores: Int): String? {
    val freqs = ArrayList<Long>()
    for (i in 0 until cores) {
        val khz = safe {
            File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull()
        }
        if (khz != null && khz > 0L) freqs.add(khz)
    }
    if (freqs.isEmpty()) return null
    return freqs.groupingBy { it }.eachCount().toSortedMap().entries
        .joinToString(" + ") { "${it.value}× ${"%.2f".format(it.key / 1_000_000.0)} GHz" }
}

private fun processorSection(): InfoSection {
    val cores = Runtime.getRuntime().availableProcessors()
    val rows = ArrayList<InfoRow>()
    rows.add(InfoRow("Architecture", Build.SUPPORTED_ABIS.joinToString(", ")))
    rows.add(InfoRow("Cores", cores.toString()))
    cpuClocks(cores)?.let { rows.add(InfoRow("Max clocks", it)) }
    return InfoSection("Processor", rows)
}

private fun memorySection(context: Context): InfoSection {
    val rows = ArrayList<InfoRow>()
    val am = context.getSystemService(ActivityManager::class.java)
    if (am != null) {
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val pct = if (mi.totalMem > 0L) (mi.availMem * 100L / mi.totalMem).toInt() else 0
        rows.add(InfoRow("RAM total", gb(mi.totalMem)))
        rows.add(InfoRow("RAM available", "${gb(mi.availMem)} ($pct%)"))
        rows.add(InfoRow("Low memory", yesNo(mi.lowMemory)))
    }
    val stat = StatFs(Environment.getDataDirectory().path)
    rows.add(InfoRow("Storage total", "%.1f GB".format(stat.totalBytes / 1_000_000_000.0)))
    rows.add(InfoRow("Storage free", "%.1f GB".format(stat.availableBytes / 1_000_000_000.0)))
    return InfoSection("Memory and storage", rows)
}

private fun displaySection(context: Context): InfoSection {
    val rows = ArrayList<InfoRow>()
    val dm = context.resources.displayMetrics
    rows.add(InfoRow("Resolution", "${dm.widthPixels} × ${dm.heightPixels}"))
    rows.add(InfoRow("Density", "${dm.densityDpi} dpi (${"%.2f".format(dm.density)}×)"))
    rows.add(InfoRow("Smallest width", "${context.resources.configuration.smallestScreenWidthDp} dp"))
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    if (display != null) {
        rows.add(InfoRow("Refresh rate", "${display.refreshRate.roundToInt()} Hz"))
        val modes = display.supportedModes.map { it.refreshRate.roundToInt() }.distinct().sorted()
        if (modes.size > 1) rows.add(InfoRow("Supported rates", modes.joinToString(", ") + " Hz"))
    }
    return InfoSection("Display", rows)
}

private fun chargeStatus(code: Int): String = when (code) {
    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
    BatteryManager.BATTERY_STATUS_FULL -> "Full"
    else -> "Unknown"
}

private fun healthText(code: Int): String = when (code) {
    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
    else -> "Unknown"
}

private fun batterySection(context: Context): InfoSection {
    val rows = ArrayList<InfoRow>()
    val intent = safe { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    var levelPct = -1
    var status = BatteryManager.BATTERY_STATUS_UNKNOWN
    if (intent != null) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) {
            levelPct = level * 100 / scale
            rows.add(InfoRow("Level", "$levelPct%"))
        }
        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        rows.add(InfoRow("Status", chargeStatus(status)))
        rows.add(InfoRow("Health", healthText(intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))))

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val source = when {
            (plugged and BatteryManager.BATTERY_PLUGGED_AC) != 0 -> "AC"
            (plugged and BatteryManager.BATTERY_PLUGGED_USB) != 0 -> "USB"
            (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0 -> "Wireless"
            else -> "Not plugged in"
        }
        rows.add(InfoRow("Power source", source))

        val mv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        if (mv > 0) rows.add(InfoRow("Voltage", "%.2f V".format(mv / 1000.0)))
        val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        if (tenths != Int.MIN_VALUE) rows.add(InfoRow("Temperature", "%.1f °C".format(tenths / 10.0)))
        intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() }
            ?.let { rows.add(InfoRow("Technology", it)) }
        if (Build.VERSION.SDK_INT >= 34) {
            val cycles = intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)
            if (cycles > 0) rows.add(InfoRow("Charge cycles", cycles.toString()))
        }
    }

    val bm = context.getSystemService(BatteryManager::class.java)
    if (bm != null) {
        val counterUah = safe { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) } ?: Int.MIN_VALUE
        if (counterUah != Int.MIN_VALUE && counterUah > 0) {
            rows.add(InfoRow("Charge remaining", "${counterUah / 1000} mAh"))
            if (levelPct in 5..100) {
                val full = (counterUah / 1000.0) * 100.0 / levelPct
                rows.add(InfoRow("Full capacity", "≈ ${full.roundToInt()} mAh (estimate)"))
            }
        }
        val currentUa = safe { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } ?: Int.MIN_VALUE
        if (currentUa != Int.MIN_VALUE && currentUa != 0) {
            val direction = if (status == BatteryManager.BATTERY_STATUS_CHARGING) "charging" else "discharging"
            rows.add(InfoRow("Current draw", "≈ ${abs(currentUa) / 1000} mA ($direction)"))
        }
    }
    return InfoSection("Battery", rows)
}

private fun powerSection(context: Context): InfoSection {
    val rows = ArrayList<InfoRow>()
    val pm = context.getSystemService(PowerManager::class.java)
    if (pm != null) {
        rows.add(InfoRow("Screen on", yesNo(pm.isInteractive)))
        rows.add(InfoRow("Battery saver", yesNo(pm.isPowerSaveMode)))
        rows.add(InfoRow("Doze (device idle)", yesNo(pm.isDeviceIdleMode)))
        rows.add(InfoRow("Unrestricted for DrainScout", yesNo(pm.isIgnoringBatteryOptimizations(context.packageName))))
    }
    val elapsed = SystemClock.elapsedRealtime()
    val awake = SystemClock.uptimeMillis()
    if (elapsed > 0L) {
        val asleepPct = ((elapsed - awake).coerceAtLeast(0L) * 100L / elapsed).toInt()
        rows.add(InfoRow("Since boot", formatUptime(elapsed)))
        rows.add(InfoRow("CPU awake", formatUptime(awake)))
        rows.add(InfoRow("Deep sleep", "$asleepPct% of uptime"))
    }
    val sensors = safe { context.getSystemService(SensorManager::class.java)?.getSensorList(Sensor.TYPE_ALL)?.size }
    if (sensors != null) rows.add(InfoRow("Sensors", sensors.toString()))
    return InfoSection("Power and uptime", rows)
}

private fun shizukuSection(): InfoSection {
    val rows = ArrayList<InfoRow>()
    val running = safe { Shizuku.pingBinder() } ?: false
    rows.add(InfoRow("Service", if (running) "Running" else "Not running"))
    if (running) {
        safe { Shizuku.getVersion() }?.let { rows.add(InfoRow("Version", it.toString())) }
        safe { Shizuku.getUid() }?.let {
            val who = when (it) {
                0 -> "root"
                2000 -> "adb shell"
                else -> "uid $it"
            }
            rows.add(InfoRow("Runs as", who))
        }
        rows.add(InfoRow("Permission for DrainScout", yesNo(DiagnosticEngine.hasShellPermission())))
    }
    rows.add(InfoRow("Command channel", Shell.mode))
    Shell.lastError?.let { rows.add(InfoRow("Last shell error", it)) }
    return InfoSection("Shizuku", rows)
}
