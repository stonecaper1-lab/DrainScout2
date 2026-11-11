package com.drain.scout

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.time.Instant
import java.util.concurrent.TimeUnit

// ---------------------------------------------------------------- Activity

class MainActivity : ComponentActivity() {

    private val viewModel: DrainScoutViewModel by viewModels()

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            viewModel.refreshPermissionState()
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        viewModel.refreshPermissionState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        viewModel.refreshPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        setContent {
            DrainScoutApp(
                viewModel = viewModel,
                onRequestShizuku = ::requestShizuku,
                onShare = ::shareReport
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                Toast.makeText(this, "Start Shizuku first, then try again", Toast.LENGTH_LONG).show()
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                viewModel.refreshPermissionState()
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(
                    this,
                    "Permission denied permanently. Allow DrainScout inside the Shizuku app.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Shizuku unavailable: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareReport(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "DrainScout report")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share report"))
    }

    private companion object {
        const val REQUEST_CODE = 4001
    }
}

// ---------------------------------------------------------------- Diagnostics

internal data class DiagnosticSnapshot(
    val battery: String,
    val power: String,
    val alarms: String,
    val deviceIdle: String,
    val capturedAt: Instant,
    val permissionStatus: String
) {
    fun toReport(): String = buildString {
        appendLine("DrainScout report")
        appendLine("Captured: $capturedAt")
        appendLine("Permission status: $permissionStatus")
        appendLine("Interpretation: treat this as triage evidence, not final causation.")
        appendLine()
        appendLine("== Battery ==")
        appendLine(battery.ifBlank { "No output returned." })
        appendLine()
        appendLine("== Power / wakelocks ==")
        appendLine(power.ifBlank { "No output returned." })
        appendLine()
        appendLine("== Alarms ==")
        appendLine(alarms.ifBlank { "No output returned." })
        appendLine()
        appendLine("== Device idle ==")
        appendLine(deviceIdle.ifBlank { "No output returned." })
    }
}

internal enum class Severity { INFO, WATCH, LIKELY }

internal data class Finding(val title: String, val detail: String, val severity: Severity)

internal object DiagnosticEngine {
    private const val GRANTED_LABEL = "Shizuku-authorized shell"
    private const val DENIED_LABEL = "No shell permission"
    private const val UNAVAILABLE = "Command unavailable in this execution context."

    const val PERMISSION_OK_LABEL = GRANTED_LABEL

    fun hasShellPermission(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun collectSnapshot(): DiagnosticSnapshot {
        val allowed = hasShellPermission()
        val permissionStatus = if (allowed) GRANTED_LABEL else DENIED_LABEL

        val battery = if (allowed) runCommand("dumpsys", "batterystats", "--charged") else UNAVAILABLE
        val power = if (allowed) runCommand("dumpsys", "power") else UNAVAILABLE
        val alarms = if (allowed) runCommand("dumpsys", "alarm") else UNAVAILABLE
        val idle = if (allowed) runCommand("dumpsys", "deviceidle") else UNAVAILABLE

        return DiagnosticSnapshot(
            battery = summarize(battery, listOf("Estimated power", "Uid", "wake", "screen", "Statistics since")),
            power = summarize(power, listOf("wake lock", "wakelock", "suspend blocker", "mWakefulness", "PARTIAL_WAKE_LOCK", "held")),
            alarms = summarize(alarms, listOf("Alarm Stats", "PendingIntent", "RTC_WAKEUP", "ELAPSED_REALTIME_WAKEUP", "whenElapsed")),
            deviceIdle = summarize(idle, listOf("mState=", "mLightState=", "IDLE", "mScreenOn", "mCharging")),
            capturedAt = Instant.now(),
            permissionStatus = permissionStatus
        )
    }

    fun summarizeFindings(snapshot: DiagnosticSnapshot): List<Finding> = buildList {
        val powerText = snapshot.power
        val alarmsText = snapshot.alarms
        val idleText = snapshot.deviceIdle

        if (powerText.contains("wake", true)) {
            add(Finding("Potential wake-lock activity", "Power output contains wake-related lines. Inspect the exact owner and duration; this is not proof of root cause.", Severity.WATCH))
        } else {
            add(Finding("No obvious wake-lock signal", "The filtered output did not match common wakelock markers. Device-specific formatting may hide them.", Severity.INFO))
        }

        if (alarmsText.contains("Alarm Stats", true) || alarmsText.contains("PendingIntent", true) || alarmsText.contains("WAKEUP", true)) {
            add(Finding("Alarm traffic detected", "Alarm-related lines appeared in the snapshot. Repeating alarms can contribute to idle drain, especially during screen-off time.", Severity.WATCH))
        } else {
            add(Finding("No alarm signal matched", "No obvious alarm summary lines appeared in the filtered result. This could be vendor-specific output.", Severity.INFO))
        }

        if (idleText.contains("IDLE", true) || idleText.contains("mState=", true)) {
            add(Finding("Idle state visible", "The device-idle dump returned state markers, which helps confirm the capture happened under idle or low-activity conditions.", Severity.INFO))
        } else {
            add(Finding("Idle-state visibility weak", "No common idle markers showed up. Compare multiple captures for confidence.", Severity.WATCH))
        }

        add(Finding("Best comparison method", "Capture at the same battery level and screen-off interval, then compare two or more snapshots before drawing conclusions.", Severity.INFO))
    }

    /**
     * Shizuku.newProcess is private since API 13.1, so it is invoked reflectively.
     * Stdout is drained on a separate thread so large dumpsys output cannot fill the pipe and deadlock.
     */
    private fun runCommand(vararg args: String): String = try {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
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

        val completed = process.waitFor(25, TimeUnit.SECONDS)
        if (!completed) process.destroy()
        reader.join(2_000)
        output.ifBlank { UNAVAILABLE }
    } catch (_: Throwable) {
        UNAVAILABLE
    }

    private fun summarize(raw: String, patterns: List<String>): String {
        val selected = raw.lineSequence()
            .filter { line -> patterns.any { pattern -> line.contains(pattern, ignoreCase = true) } }
            .take(80)
            .toList()

        return if (selected.isEmpty()) {
            "No common summary lines matched this device output. Output may use different wording or filtering may be too strict."
        } else {
            selected.joinToString("\n").take(12000)
        }
    }
}

// ---------------------------------------------------------------- ViewModel

internal data class DiagnosticsUiState(
    val hasPermission: Boolean = false,
    val loading: Boolean = false,
    val snapshot: DiagnosticSnapshot? = null,
    val error: String? = null
)

internal class DrainScoutViewModel : ViewModel() {
    private val _state = MutableStateFlow(DiagnosticsUiState())
    val state: StateFlow<DiagnosticsUiState> = _state.asStateFlow()

    fun refreshPermissionState() {
        _state.value = _state.value.copy(hasPermission = DiagnosticEngine.hasShellPermission())
    }

    fun captureSnapshot() {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { DiagnosticEngine.collectSnapshot() }
                _state.value = _state.value.copy(
                    loading = false,
                    snapshot = snapshot,
                    hasPermission = snapshot.permissionStatus == DiagnosticEngine.PERMISSION_OK_LABEL,
                    error = null
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = t.message ?: "Capture failed."
                )
            }
        }
    }
}

// ---------------------------------------------------------------- UI

private val BgDark = Color(0xFF07131B)
private val BgPanel = Color(0xFF0E1E2A)
private val TextPrimary = Color(0xFFE6F3FF)
private val TextSecondary = Color(0xFF9CC8FF)
private val TextMuted = Color(0xFF9BB0BF)
private val Success = Color(0xFF8AE6C9)
private val Warning = Color(0xFFFFCF78)
private val ErrorColor = Color(0xFFFFB4B4)

private enum class TabSpec(val title: String) {
    OVERVIEW("Overview"),
    WAKELOCKS("Wakelocks"),
    ALARMS("Alarms"),
    IDLE("Idle"),
    REPORT("Report")
}

@Composable
internal fun DrainScoutApp(
    viewModel: DrainScoutViewModel,
    onRequestShizuku: () -> Unit,
    onShare: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val snapshot = state.snapshot
    val report = snapshot?.toReport().orEmpty()
    val findings = remember(snapshot) { snapshot?.let(DiagnosticEngine::summarizeFindings).orEmpty() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = TabSpec.values()

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState()
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BgDark,
            surface = BgDark,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            primary = Success,
            onPrimary = BgDark
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BgDark) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("DrainScout", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("Idle drain investigation", color = TextSecondary)
                    Text(
                        text = if (state.hasPermission) "Shizuku shell ready" else "Shizuku permission required",
                        color = if (state.hasPermission) Success else Warning
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!state.hasPermission) {
                            Button(onClick = onRequestShizuku) { Text("Connect Shizuku") }
                        }
                        Button(onClick = viewModel::captureSnapshot, enabled = !state.loading) {
                            Text(if (state.loading) "Capturing…" else "Capture baseline")
                        }
                        if (snapshot != null) {
                            Button(onClick = { clipboard.setText(AnnotatedString(report)) }) { Text("Copy") }
                            Button(onClick = { onShare(report) }) { Text("Share") }
                        }
                    }

                    state.error?.let { Text(it, color = ErrorColor) }
                    snapshot?.let { Text("Last capture: ${it.capturedAt}", color = TextMuted) }
                }

                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = BgDark,
                    contentColor = Success,
                    edgePadding = 12.dp
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(tab.title) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (snapshot == null) {
                        PanelText("No capture yet. Connect Shizuku, then tap Capture baseline.")
                    } else {
                        when (tabs[selectedTab]) {
                            TabSpec.OVERVIEW -> findings.forEach { FindingCard(it) }
                            TabSpec.WAKELOCKS -> PanelText(snapshot.power, mono = true)
                            TabSpec.ALARMS -> PanelText(snapshot.alarms, mono = true)
                            TabSpec.IDLE -> PanelText(snapshot.deviceIdle, mono = true)
                            TabSpec.REPORT -> PanelText(report, mono = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingCard(finding: Finding) {
    val accent = when (finding.severity) {
        Severity.INFO -> Success
        Severity.WATCH -> Warning
        Severity.LIKELY -> ErrorColor
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgPanel)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(finding.title, color = accent, style = MaterialTheme.typography.titleMedium)
            Text(finding.detail, color = TextPrimary)
        }
    }
}

@Composable
private fun PanelText(text: String, mono: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = BgPanel)
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontSize = if (mono) 12.sp else 14.sp,
            modifier = Modifier.fillMaxWidth().background(BgPanel).padding(14.dp)
        )
    }
}
