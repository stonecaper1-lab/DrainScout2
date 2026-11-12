package com.drain.scout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFFE9F4FF)
private val InkDim = Color(0xFFA9C4DB)
private val Mint = Color(0xFF8AE6C9)
private val Amber = Color(0xFFFFCF78)
private val Coral = Color(0xFFFF9A9A)
private val Sky = Color(0xFF9CC8FF)

private val TAB_TITLES = listOf("Overview", "Wakelocks", "Alarms", "Doze", "Device", "Report", "About")

// ---------------------------------------------------------------- glass kit

/** Static glow background. Deliberately not animated: a battery tool should not burn battery. */
@Composable
private fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF040A12), Color(0xFF0B1D2C))))
            .drawBehind {
                glow(Offset(size.width * 0.15f, size.height * 0.10f), size.minDimension * 0.75f, Color(0x558AE6C9))
                glow(Offset(size.width * 0.95f, size.height * 0.40f), size.minDimension * 0.70f, Color(0x449CC8FF))
                glow(Offset(size.width * 0.20f, size.height * 0.90f), size.minDimension * 0.80f, Color(0x44B48CFF))
            },
        content = content
    )
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(colors = listOf(color, Color.Transparent), center = center, radius = radius),
        radius = radius,
        center = center
    )
}

@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0x38FFFFFF), Color(0x0FFFFFFF))))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x14FFFFFF), Color(0x4DFFFFFF))),
                shape
            )
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun GlassButton(
    text: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val fill = if (primary) {
        listOf(Color(0x808AE6C9), Color(0x338AE6C9))
    } else {
        listOf(Color(0x40FFFFFF), Color(0x14FFFFFF))
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Brush.verticalGradient(fill))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x22FFFFFF))), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Ink else InkDim,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x1AFFFFFF))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x1AFFFFFF))), shape)
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TAB_TITLES.forEachIndexed { index, title ->
                val on = index == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (on) Color(0x4D8AE6C9) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        maxLines = 1,
                        color = if (on) Ink else InkDim,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = InkDim,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp)
    )
}

@Composable
private fun BarRow(label: String, value: String, fraction: Float, color: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = label,
                color = Ink,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(text = value, color = InkDim, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x22FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    GlassCard { Text(message, color = InkDim, fontSize = 14.sp) }
}

// ---------------------------------------------------------------- screens

@Composable
internal fun DrainScoutApp(
    viewModel: DrainScoutViewModel,
    onRequestShizuku: () -> Unit,
    onShare: (String) -> Unit,
    onToggleUpdates: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    var tab by remember { mutableIntStateOf(0) }

    val snapshot = state.snapshot
    val drain = remember(snapshot, state.refLevel, state.refTime) {
        val level = snapshot?.battery?.level
        val refLevel = state.refLevel
        val refTime = state.refTime
        if (snapshot != null && level != null && refLevel != null && refTime != null) {
            drainSince(refLevel, refTime, level, snapshot.capturedAt)
        } else {
            null
        }
    }
    val report = remember(snapshot, drain) { snapshot?.let { buildReport(it, drain) }.orEmpty() }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF040A12),
            surface = Color(0xFF040A12),
            onBackground = Ink,
            onSurface = Ink,
            primary = Mint,
            onPrimary = Color(0xFF040A12)
        )
    ) {
        GlassBackground {
            Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                    Text("DrainScout", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (state.hasPermission) Mint else Amber)
                        )
                        Text(
                            text = if (state.hasPermission) "Shizuku connected" else "Shizuku permission needed",
                            color = InkDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!state.hasPermission) {
                        GlassButton("Connect Shizuku", onClick = onRequestShizuku)
                    }
                    GlassButton(
                        text = if (state.loading) "Capturing…" else "Capture",
                        primary = true,
                        enabled = !state.loading,
                        onClick = viewModel::capture
                    )
                    if (snapshot != null) {
                        GlassButton("Set reference", onClick = viewModel::setReference)
                        GlassButton("Copy", onClick = { clipboard.setText(AnnotatedString(report)) })
                        GlassButton("Share", onClick = { onShare(report) })
                    }
                    GlassButton(
                        text = if (state.updateAlerts) "Update alerts: On" else "Update alerts: Off",
                        onClick = onToggleUpdates
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.error?.let { Text(it, color = Coral, fontSize = 13.sp) }
                    state.refTime?.let {
                        Text(
                            "Reference: ${state.refLevel}% at ${formatTime(it)}",
                            color = InkDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    if (tab == 4) {
                        DeviceTab()
                    } else if (tab == 6) {
                        AboutTab(state.updateAlerts)
                    } else if (snapshot == null) {
                        EmptyCard("No capture yet. Connect Shizuku, then tap Capture. The first capture also becomes your reference point; capture again later to see the drain rate.")
                    } else {
                        when (tab) {
                            0 -> OverviewTab(snapshot, drain)
                            1 -> WakelocksTab(snapshot)
                            2 -> AlarmsTab(snapshot)
                            3 -> DozeTab(snapshot)
                            else -> GlassCard {
                                Text(
                                    text = report,
                                    color = Ink,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                GlassTabBar(selected = tab, onSelect = { tab = it })
            }
        }
    }
}

@Composable
private fun OverviewTab(s: Snapshot, drain: Drain?) {
    val b = s.battery
    if (b != null) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${b.level}%", color = Ink, fontSize = 52.sp, fontWeight = FontWeight.Bold)
                Column(modifier = Modifier.padding(start = 18.dp)) {
                    Text(
                        text = b.status + if (b.plugged) " · plugged in" else "",
                        color = Ink,
                        fontSize = 15.sp
                    )
                    Text("%.1f °C".format(b.tempC), color = InkDim, fontSize = 14.sp)
                    Text("Captured ${formatTime(s.capturedAt)}", color = InkDim, fontSize = 12.sp)
                }
            }
            if (drain != null) {
                val rate = "%.1f".format(drain.perHour)
                val line = when {
                    drain.deltaPct > 0 -> "−${drain.deltaPct}% in ${formatDuration(drain.minutes)} · $rate %/h"
                    drain.deltaPct == 0 -> "No change in ${formatDuration(drain.minutes)}"
                    else -> "Charged +${-drain.deltaPct}% since reference. Set a new reference."
                }
                Text(
                    text = line,
                    color = if (drain.perHour >= 4f) Coral else if (drain.perHour >= 2f) Amber else Mint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }

    SectionTitle("Findings")
    buildFindings(s, drain).forEach { f ->
        val accent = when (f.severity) {
            Severity.INFO -> Mint
            Severity.WATCH -> Amber
            Severity.ALERT -> Coral
        }
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
                Text(
                    text = f.title,
                    color = accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(f.detail, color = Ink, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }

    SectionTitle("Top consumers (estimated mAh)")
    if (s.consumers.isEmpty()) {
        EmptyCard("No power estimates were found in this device's batterystats output.")
    } else {
        val max = s.consumers.maxOf { it.mah }.coerceAtLeast(0.1f)
        GlassCard {
            s.consumers.forEach {
                BarRow(it.label, "%.1f".format(it.mah), it.mah / max, Sky)
            }
        }
    }
}

@Composable
private fun VerdictChip(verdict: Verdict, label: String) {
    val color = if (verdict == Verdict.USUAL) Mint else Coral
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.20f))
            .border(1.dp, color.copy(alpha = 0.6f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun JudgedCard(title: String, subtitle: String, j: Judgement) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            VerdictChip(j.verdict, if (j.verdict == Verdict.USUAL) "Usual" else "Unusual")
        }
        Text(
            text = subtitle,
            color = InkDim,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        Text(
            text = j.kind,
            color = Sky,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(j.description, color = Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Text(
            text = j.reason,
            color = if (j.verdict == Verdict.USUAL) Mint else Coral,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun WakelocksTab(s: Snapshot) {
    val judged = s.wakeLocks
        .map { it to judgeWakeLock(it, s.wakefulness) }
        .sortedByDescending { it.second.verdict == Verdict.UNUSUAL }
    val unusual = judged.count { it.second.verdict == Verdict.UNUSUAL }

    SectionTitle("Held right now")
    if (judged.isEmpty()) {
        EmptyCard("No wake locks are held at the moment of capture, or this device formats them differently.")
    } else {
        GlassCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                VerdictChip(Verdict.USUAL, "${judged.size - unusual} usual")
                VerdictChip(Verdict.UNUSUAL, "$unusual unusual")
            }
            Text(
                text = "Screen state: ${s.wakefulness ?: "unknown"}. Limits are stricter while the screen is off.",
                color = InkDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        judged.forEach { (w, j) ->
            val held = w.heldMs?.let { formatHeld(it) } ?: "unknown"
            JudgedCard(w.owner, "${w.type} · '${w.tag}' · held $held", j)
        }
    }

    SectionTitle("Kernel wakelocks (since last charge)")
    if (s.kernelWakelocks.isEmpty()) {
        EmptyCard("No kernel wakelock lines were reported. Newer Android versions often omit them.")
    } else {
        s.kernelWakelocks.forEach { k ->
            JudgedCard(k.name, "${k.count}× · ${k.duration}", judgeKernelWakelock(k))
        }
    }
}

@Composable
private fun AlarmsTab(s: Snapshot) {
    SectionTitle("Alarm wakeups by app")
    if (s.alarms.isEmpty()) {
        EmptyCard("No per-app alarm statistics were found in this device's output.")
    } else {
        val max = s.alarms.maxOf { it.wakeups }.coerceAtLeast(1)
        GlassCard {
            s.alarms.forEach {
                BarRow(it.owner, "${it.wakeups}", it.wakeups.toFloat() / max, Amber)
            }
        }
    }
}

@Composable
private fun DozeTab(s: Snapshot) {
    val d = s.doze
    if (d == null) {
        EmptyCard("Doze state was not reported by this device.")
        return
    }
    GlassCard {
        Text("Deep Doze", color = InkDim, fontSize = 13.sp)
        Text(d.deep, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("Light Doze", color = InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        Text(d.light, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    }
    EmptyCard("IDLE means the system is deeply idling. ACTIVE means it is not. A phone that sits ACTIVE for hours while the screen is off is worth investigating.")
}

@Composable
private fun InfoLine(row: InfoRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(row.label, color = InkDim, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
        Text(
            text = row.value,
            color = Ink,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun DeviceTab() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val sections = remember(refresh) { collectSystemInfo(context) }

    GlassButton("Refresh", onClick = { refresh++ })
    sections.forEach { section ->
        SectionTitle(section.title)
        GlassCard {
            section.rows.forEach { InfoLine(it) }
        }
    }
}

@Composable
private fun AboutTab(updateAlerts: Boolean) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
            .orEmpty()
    }
    val open: (String) -> Unit = { url ->
        try {
            uriHandler.openUri(url)
        } catch (_: Throwable) {
        }
    }

    GlassCard {
        Text("DrainScout", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Version $version", color = InkDim, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        Text(
            text = "Designed with care, calibrated for 15 and 9",
            color = Mint,
            fontSize = 15.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 14.dp)
        )
    }

    SectionTitle("What it does")
    GlassCard {
        Text(
            "Reads battery, power, alarm and Doze state through Shizuku, then points at the apps and wake locks most likely to be draining your battery. Capture twice and compare to see a drain rate since your reference point.",
            color = Ink,
            fontSize = 14.sp
        )
    }

    SectionTitle("How wake locks are judged")
    GlassCard {
        Text(
            "Each wake lock is matched to a known kind (push, alarm, job, location, audio) and compared with how long that kind is normally held. Limits are tighter while the screen is off. They are DrainScout heuristics, not official Android thresholds. A single capture is a lead, not a verdict.",
            color = Ink,
            fontSize = 14.sp
        )
    }

    SectionTitle("Privacy")
    GlassCard {
        Text(
            text = "Nothing leaves your device. The only network request is an update check against the GitHub releases API, currently " +
                (if (updateAlerts) "on" else "off") +
                ". Switch it with the Update alerts button.",
            color = Ink,
            fontSize = 14.sp
        )
    }

    SectionTitle("Project")
    GlassCard {
        Text("Open source. Built on Shizuku, inspired by BetterBatteryStats.", color = Ink, fontSize = 14.sp)
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GlassButton("Source code", onClick = { open("https://github.com/stonecaper1-lab/DrainScout2") })
            GlassButton("Releases", onClick = { open("https://github.com/stonecaper1-lab/DrainScout2/releases") })
        }
    }
}
