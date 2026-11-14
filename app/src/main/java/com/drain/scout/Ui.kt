package com.drain.scout

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

internal object Pal {
    val INK = 0xFFE9F4FF.toInt()
    val DIM = 0xFFA9C4DB.toInt()
    val MINT = 0xFF8AE6C9.toInt()
    val AMBER = 0xFFFFCF78.toInt()
    val CORAL = 0xFFFF9A9A.toInt()
    val SKY = 0xFF9CC8FF.toInt()
    val VIOLET = 0xFFB48CFF.toInt()
    val BG = 0xFF050B14.toInt()
}

internal fun withAlpha(alpha: Int, color: Int): Int = (alpha shl 24) or (color and 0xFFFFFF)

internal fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

// ------------------------------------------------------------------ custom views

/** Static glow background. Not animated on purpose: a battery tool should not burn battery. */
internal class GlowView(context: Context) : View(context) {
    private class Blob(val x: Float, val y: Float, val r: Float, val shader: RadialGradient)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var blobs: List<Blob> = emptyList()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val s = minOf(w, h).toFloat()
        blobs = listOf(
            blob(w * 0.12f, h * 0.06f, s * 0.95f, withAlpha(0x70, Pal.MINT)),
            blob(w * 0.98f, h * 0.42f, s * 0.80f, withAlpha(0x55, Pal.SKY)),
            blob(w * 0.15f, h * 0.92f, s * 0.95f, withAlpha(0x55, Pal.VIOLET))
        )
    }

    private fun blob(x: Float, y: Float, r: Float, color: Int): Blob =
        Blob(x, y, r, RadialGradient(x, y, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP))

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Pal.BG)
        for (b in blobs) {
            paint.shader = b.shader
            canvas.drawCircle(b.x, b.y, b.r, paint)
        }
    }
}

/** Battery ring: arc for the level, percentage in the middle, state underneath. */
internal class RingView(context: Context) : View(context) {
    var percent: Int = 0
        set(value) {
            field = value
            invalidate()
        }
    var accent: Int = Pal.MINT
        set(value) {
            field = value
            invalidate()
        }
    var caption: String = ""
        set(value) {
            field = value
            invalidate()
        }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0x26FFFFFF
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.INK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Pal.DIM
        textAlign = Paint.Align.CENTER
    }
    private val box = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = w * 0.085f
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        arc.color = accent
        val inset = stroke / 2f + 1f
        box.set(inset, inset, w - inset, h - inset)
        canvas.drawArc(box, 0f, 360f, false, track)
        canvas.drawArc(box, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, arc)
        big.textSize = w * 0.27f
        small.textSize = w * 0.105f
        val cy = h / 2f
        canvas.drawText("$percent%", w / 2f, cy - (big.ascent() + big.descent()) / 2f - w * 0.06f, big)
        canvas.drawText(caption, w / 2f, cy + w * 0.22f, small)
    }
}

// ------------------------------------------------------------------ building blocks

internal fun label(
    ctx: Context,
    text: CharSequence,
    sp: Float = 14f,
    color: Int = Pal.INK,
    bold: Boolean = false,
    mono: Boolean = false
): TextView {
    val v = TextView(ctx)
    v.text = text
    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    v.setTextColor(color)
    if (bold) v.typeface = Typeface.DEFAULT_BOLD
    if (mono) v.typeface = Typeface.MONOSPACE
    return v
}

internal fun glass(ctx: Context, radiusDp: Int, strong: Boolean = false): GradientDrawable {
    val g = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(if (strong) 0x4DFFFFFF else 0x33FFFFFF, 0x0DFFFFFF)
    )
    g.cornerRadius = ctx.dp(radiusDp).toFloat()
    g.setStroke(ctx.dp(1), 0x45FFFFFF)
    return g
}

internal fun card(ctx: Context): LinearLayout {
    val c = LinearLayout(ctx)
    c.orientation = LinearLayout.VERTICAL
    c.background = glass(ctx, 24)
    val p = ctx.dp(18)
    c.setPadding(p, p, p, p)
    return c
}

internal fun LinearLayout.addBlock(v: View, bottomDp: Int = 12) {
    val lp = LinearLayout.LayoutParams(MATCH, WRAP)
    lp.bottomMargin = context.dp(bottomDp)
    addView(v, lp)
}

internal fun LinearLayout.section(text: String) {
    val t = label(context, text, 13f, Pal.DIM, bold = true)
    t.setPadding(context.dp(6), context.dp(8), 0, context.dp(8))
    addView(t)
}

internal fun pill(
    ctx: Context,
    text: String,
    primary: Boolean = false,
    sp: Float = 14f,
    hPadDp: Int = 14,
    onClick: () -> Unit
): TextView {
    val fill = if (primary) {
        intArrayOf(withAlpha(0xA0, Pal.MINT), withAlpha(0x45, Pal.MINT))
    } else {
        intArrayOf(0x44FFFFFF, 0x18FFFFFF)
    }
    val shape = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, fill)
    shape.cornerRadius = ctx.dp(22).toFloat()
    shape.setStroke(ctx.dp(1), 0x55FFFFFF)
    val v = TextView(ctx)
    v.text = text
    v.gravity = Gravity.CENTER
    v.maxLines = 1
    v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    v.setTextColor(Pal.INK)
    v.typeface = Typeface.DEFAULT_BOLD
    v.setPadding(ctx.dp(hPadDp), ctx.dp(11), ctx.dp(hPadDp), ctx.dp(11))
    v.background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
    v.isClickable = true
    v.setOnClickListener { onClick() }
    return v
}

internal fun chip(ctx: Context, text: String, color: Int): TextView {
    val bg = GradientDrawable()
    bg.cornerRadius = ctx.dp(12).toFloat()
    bg.setColor(withAlpha(0x33, color))
    bg.setStroke(ctx.dp(1), withAlpha(0x99, color))
    val v = label(ctx, text, 12f, color, bold = true)
    v.setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(4))
    v.background = bg
    return v
}

internal fun dot(ctx: Context, color: Int, sizeDp: Int = 8): View {
    val g = GradientDrawable()
    g.shape = GradientDrawable.OVAL
    g.setColor(color)
    val v = View(ctx)
    v.background = g
    v.layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    return v
}

internal fun barRow(ctx: Context, name: String, value: String, fraction: Float, color: Int): View {
    val box = LinearLayout(ctx)
    box.orientation = LinearLayout.VERTICAL
    box.setPadding(0, ctx.dp(5), 0, ctx.dp(5))

    val top = LinearLayout(ctx)
    top.orientation = LinearLayout.HORIZONTAL
    val n = label(ctx, name, 14f)
    n.maxLines = 1
    n.ellipsize = TextUtils.TruncateAt.END
    top.addView(n, LinearLayout.LayoutParams(0, WRAP, 1f))
    val v = label(ctx, value, 13f, Pal.DIM)
    v.setPadding(ctx.dp(8), 0, 0, 0)
    top.addView(v)
    box.addView(top)

    val f = fraction.coerceIn(0.03f, 1f)
    val trackBg = GradientDrawable()
    trackBg.cornerRadius = ctx.dp(3).toFloat()
    trackBg.setColor(0x22FFFFFF)
    val track = LinearLayout(ctx)
    track.orientation = LinearLayout.HORIZONTAL
    track.background = trackBg
    val fillBg = GradientDrawable()
    fillBg.cornerRadius = ctx.dp(3).toFloat()
    fillBg.setColor(color)
    val fill = View(ctx)
    fill.background = fillBg
    track.addView(fill, LinearLayout.LayoutParams(0, ctx.dp(6), f))
    track.addView(View(ctx), LinearLayout.LayoutParams(0, ctx.dp(6), 1f - f))
    val lp = LinearLayout.LayoutParams(MATCH, WRAP)
    lp.topMargin = ctx.dp(4)
    box.addView(track, lp)
    return box
}

internal fun infoLine(ctx: Context, row: InfoRow): View {
    val r = LinearLayout(ctx)
    r.orientation = LinearLayout.HORIZONTAL
    r.setPadding(0, ctx.dp(5), 0, ctx.dp(5))
    r.addView(label(ctx, row.label, 13f, Pal.DIM), LinearLayout.LayoutParams(0, WRAP, 0.4f))
    val valueView = label(ctx, row.value, 13f)
    valueView.gravity = Gravity.END
    r.addView(valueView, LinearLayout.LayoutParams(0, WRAP, 0.6f))
    return r
}

internal fun emptyCard(ctx: Context, message: String): View {
    val c = card(ctx)
    c.addView(label(ctx, message, 14f, Pal.DIM))
    return c
}

private fun verdictColor(v: Verdict): Int = if (v == Verdict.USUAL) Pal.MINT else Pal.CORAL

private fun judgedCard(ctx: Context, title: String, subtitle: String, j: Judgement): View {
    val c = card(ctx)
    val head = LinearLayout(ctx)
    head.orientation = LinearLayout.HORIZONTAL
    head.gravity = Gravity.CENTER_VERTICAL
    val t = label(ctx, title, 15f, Pal.INK, bold = true)
    t.maxLines = 1
    t.ellipsize = TextUtils.TruncateAt.END
    head.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
    head.addView(chip(ctx, if (j.verdict == Verdict.USUAL) "Usual" else "Unusual", verdictColor(j.verdict)))
    c.addView(head)

    val sub = label(ctx, subtitle, 12f, Pal.DIM)
    sub.maxLines = 2
    sub.ellipsize = TextUtils.TruncateAt.END
    c.addView(sub)

    val kind = label(ctx, j.kind, 12f, Pal.SKY, bold = true)
    kind.setPadding(0, ctx.dp(10), 0, 0)
    c.addView(kind)
    c.addView(label(ctx, j.description, 13f))
    val why = label(ctx, j.reason, 13f, verdictColor(j.verdict))
    why.setPadding(0, ctx.dp(6), 0, 0)
    c.addView(why)
    return c
}

// ------------------------------------------------------------------ tabs

internal fun LinearLayout.buildOverview(s: Snapshot, drain: Drain?) {
    val ctx = context
    val b = s.battery
    val hero = card(ctx)
    if (b == null) {
        hero.addView(label(ctx, "No battery data came back.", 14f, Pal.DIM))
    } else {
        val row = LinearLayout(ctx)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val ring = RingView(ctx)
        ring.percent = b.level
        ring.accent = if (b.level <= 15) Pal.CORAL else if (b.level <= 30) Pal.AMBER else Pal.MINT
        ring.caption = b.status.lowercase()
        row.addView(ring, LinearLayout.LayoutParams(ctx.dp(116), ctx.dp(116)))

        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ctx.dp(18), 0, 0, 0)
        col.addView(label(ctx, if (b.plugged) "Plugged in" else "On battery", 16f, Pal.INK, bold = true))
        col.addView(label(ctx, "%.1f °C".format(b.tempC), 14f, Pal.DIM))
        col.addView(label(ctx, "Captured ${formatTime(s.capturedAt)}", 12f, Pal.DIM))
        if (drain != null) {
            val rate = "%.1f".format(drain.perHour)
            val line = when {
                drain.deltaPct > 0 -> "−${drain.deltaPct}% in ${formatDuration(drain.minutes)} · $rate %/h"
                drain.deltaPct == 0 -> "No change in ${formatDuration(drain.minutes)}"
                else -> "Charged +${-drain.deltaPct}% since baseline"
            }
            val tone = if (drain.perHour >= 4f) Pal.CORAL else if (drain.perHour >= 2f) Pal.AMBER else Pal.MINT
            val d = label(ctx, line, 14f, tone, bold = true)
            d.setPadding(0, ctx.dp(8), 0, 0)
            col.addView(d)
        }
        row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
        hero.addView(row)
    }
    addBlock(hero)

    section("Findings")
    for (f in buildFindings(s, drain)) {
        val accent = when (f.severity) {
            Severity.INFO -> Pal.MINT
            Severity.WATCH -> Pal.AMBER
            Severity.ALERT -> Pal.CORAL
        }
        val c = card(ctx)
        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(dot(ctx, accent))
        val t = label(ctx, f.title, 15f, accent, bold = true)
        t.setPadding(ctx.dp(8), 0, 0, 0)
        head.addView(t)
        c.addView(head)
        val d = label(ctx, f.detail, 14f)
        d.setPadding(0, ctx.dp(6), 0, 0)
        c.addView(d)
        addBlock(c)
    }

    s.doze?.let { doze ->
        section("Doze")
        val c = card(ctx)
        c.addView(label(ctx, "Deep ${doze.deep}  ·  Light ${doze.light}", 15f, Pal.INK, bold = true))
        val hint = label(
            ctx,
            "IDLE means the system is deeply idling. A phone that stays ACTIVE for hours with the screen off is worth investigating.",
            13f,
            Pal.DIM
        )
        hint.setPadding(0, ctx.dp(6), 0, 0)
        c.addView(hint)
        addBlock(c)
    }

    section("Top consumers (estimated mAh)")
    if (s.consumers.isEmpty()) {
        addBlock(emptyCard(ctx, "No power estimates were found in this device's batterystats output."))
    } else {
        val max = s.consumers.maxOf { it.mah }.coerceAtLeast(0.1f)
        val c = card(ctx)
        for (consumer in s.consumers) {
            c.addView(barRow(ctx, consumer.label, "%.1f".format(consumer.mah), consumer.mah / max, Pal.SKY))
        }
        addBlock(c)
    }
}

internal fun LinearLayout.buildWakelocks(s: Snapshot) {
    val ctx = context
    val judged = s.wakeLocks
        .map { it to judgeWakeLock(it, s.wakefulness) }
        .sortedByDescending { it.second.verdict == Verdict.UNUSUAL }
    val unusual = judged.count { it.second.verdict == Verdict.UNUSUAL }

    section("Held right now")
    if (judged.isEmpty()) {
        addBlock(emptyCard(ctx, "No wake locks are held at the moment of capture, or this device formats them differently."))
    } else {
        val summary = card(ctx)
        val chips = LinearLayout(ctx)
        chips.orientation = LinearLayout.HORIZONTAL
        val usual = chip(ctx, "${judged.size - unusual} usual", Pal.MINT)
        chips.addView(usual)
        val bad = chip(ctx, "$unusual unusual", Pal.CORAL)
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.leftMargin = ctx.dp(10)
        chips.addView(bad, lp)
        summary.addView(chips)
        val note = label(
            ctx,
            "Screen state: ${s.wakefulness ?: "unknown"}. Limits are stricter while the screen is off.",
            12f,
            Pal.DIM
        )
        note.setPadding(0, ctx.dp(10), 0, 0)
        summary.addView(note)
        addBlock(summary)
        for ((w, j) in judged) {
            val held = w.heldMs?.let { formatHeld(it) } ?: "unknown"
            addBlock(judgedCard(ctx, w.owner, "${w.type} · '${w.tag}' · held $held", j))
        }
    }

    section("Kernel wakelocks (since last charge)")
    if (s.kernelWakelocks.isEmpty()) {
        addBlock(emptyCard(ctx, "No kernel wakelock lines were reported. Newer Android versions often omit them."))
    } else {
        for (k in s.kernelWakelocks) {
            addBlock(judgedCard(ctx, k.name, "${k.count}× · ${k.duration}", judgeKernelWakelock(k)))
        }
    }
}

internal fun LinearLayout.buildAlarms(s: Snapshot) {
    val ctx = context
    section("Alarm wakeups by app")
    if (s.alarms.isEmpty()) {
        addBlock(emptyCard(ctx, "No per-app alarm statistics were found in this device's output."))
        return
    }
    val max = s.alarms.maxOf { it.wakeups }.coerceAtLeast(1)
    val c = card(ctx)
    for (a in s.alarms) c.addView(barRow(ctx, a.owner, "${a.wakeups}", a.wakeups.toFloat() / max, Pal.AMBER))
    addBlock(c)
}

internal fun LinearLayout.buildDevice(onRefresh: () -> Unit) {
    val ctx = context
    val refresh = pill(ctx, "Refresh", sp = 13f, onClick = onRefresh)
    val lp = LinearLayout.LayoutParams(WRAP, WRAP)
    lp.bottomMargin = ctx.dp(4)
    addView(refresh, lp)
    for (sec in collectSystemInfo(ctx)) {
        section(sec.title)
        val c = card(ctx)
        for (row in sec.rows) c.addView(infoLine(ctx, row))
        addBlock(c)
    }
}

internal fun LinearLayout.buildReportView(report: String) {
    val c = card(context)
    val t = label(context, report, 12f, Pal.INK, mono = true)
    t.setTextIsSelectable(true)
    c.addView(t)
    addBlock(c)
}

internal fun LinearLayout.buildAbout(updateAlerts: Boolean, onToggleUpdates: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = context
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Throwable) {
        ""
    }

    val hero = card(ctx)
    hero.addView(label(ctx, "DrainScout", 28f, Pal.INK, bold = true))
    hero.addView(label(ctx, "Version $version", 13f, Pal.DIM))
    val sig = label(ctx, "Designed with care, calibrated for 15 and 9", 15f, Pal.MINT, bold = true)
    sig.setPadding(0, ctx.dp(14), 0, 0)
    hero.addView(sig)
    addBlock(hero)

    section("What it does")
    val what = card(ctx)
    what.addView(
        label(
            ctx,
            "Reads battery, power, alarm and Doze state through Shizuku, then points at the apps and wake locks most likely to be draining your battery. Capture twice and compare to get a drain rate since your baseline.",
            14f
        )
    )
    addBlock(what)

    section("How wake locks are judged")
    val how = card(ctx)
    how.addView(
        label(
            ctx,
            "Each wake lock is matched to a known kind (push, alarm, job, location, audio) and compared with how long that kind is normally held. Limits are tighter while the screen is off. They are DrainScout heuristics, not official Android thresholds. One capture is a lead, not a verdict.",
            14f
        )
    )
    addBlock(how)

    section("Updates and privacy")
    val priv = card(ctx)
    priv.addView(
        label(
            ctx,
            "Nothing leaves your device. The only network request is a check of the GitHub releases page for a newer version, about every 6 hours.",
            14f
        )
    )
    val toggle = pill(ctx, if (updateAlerts) "Update alerts: On" else "Update alerts: Off", sp = 13f, onClick = onToggleUpdates)
    val tlp = LinearLayout.LayoutParams(WRAP, WRAP)
    tlp.topMargin = ctx.dp(12)
    priv.addView(toggle, tlp)
    addBlock(priv)

    section("Project")
    val project = card(ctx)
    project.addView(label(ctx, "Open source. Built on Shizuku, inspired by BetterBatteryStats.", 14f))
    val links = LinearLayout(ctx)
    links.orientation = LinearLayout.HORIZONTAL
    links.addView(pill(ctx, "Source code", sp = 13f) { onOpen("https://github.com/stonecaper1-lab/DrainScout2") })
    val rlp = LinearLayout.LayoutParams(WRAP, WRAP)
    rlp.leftMargin = ctx.dp(10)
    links.addView(pill(ctx, "Releases", sp = 13f) { onOpen("https://github.com/stonecaper1-lab/DrainScout2/releases") }, rlp)
    val llp = LinearLayout.LayoutParams(WRAP, WRAP)
    llp.topMargin = ctx.dp(12)
    project.addView(links, llp)
    addBlock(project)
}

internal fun LinearLayout.buildHistory(
    entries: List<HistoryEntry>,
    expanded: Set<Long>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit
) {
    val ctx = context
    section("Previous captures")
    if (entries.isEmpty()) {
        addBlock(emptyCard(ctx, "Nothing saved yet. Every successful capture is stored here, so you can compare later."))
        return
    }

    // Wake locks flagged unusual in more than one capture
    val repeat = entries.flatMap { e -> e.wakes.filter { it.unusual }.map { "${it.owner} · '${it.tag}'" }.distinct() }
        .groupingBy { it }.eachCount().filter { it.value >= 2 }.entries.sortedByDescending { it.value }.take(5)
    if (repeat.isNotEmpty()) {
        val c = card(ctx)
        c.addView(label(ctx, "Repeat offenders", 15f, Pal.INK, bold = true))
        val hint = label(ctx, "Flagged unusual in more than one capture.", 12f, Pal.DIM)
        hint.setPadding(0, 0, 0, ctx.dp(6))
        c.addView(hint)
        for ((name, n) in repeat) {
            c.addView(barRow(ctx, name, "$n of ${entries.size}", n.toFloat() / entries.size, Pal.CORAL))
        }
        addBlock(c)
    }

    entries.forEachIndexed { index, e ->
        val c = card(ctx)
        c.isClickable = true
        c.setOnClickListener { onToggle(e.time) }

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(label(ctx, formatTime(e.time), 15f, Pal.INK, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
        head.addView(chip(ctx, "${e.level}%", if (e.plugged) Pal.SKY else Pal.MINT))
        c.addView(head)

        val state = buildString {
            append("Screen ${e.screen.lowercase()}")
            append(" · ${"%.1f".format(e.tempC)}°C")
            if (e.plugged) append(" · charging")
        }
        c.addView(label(ctx, state, 12f, Pal.DIM))

        val older = entries.getOrNull(index + 1)
        if (older != null && !e.plugged && !older.plugged && e.level <= older.level) {
            val d = drainSince(older.level, older.time, e.level, e.time)
            if (d != null) {
                val t = label(
                    ctx,
                    "−${d.deltaPct}% over ${formatDuration(d.minutes)} since the previous capture (${"%.1f".format(d.perHour)}%/h)",
                    12f,
                    Pal.AMBER
                )
                t.setPadding(0, ctx.dp(6), 0, 0)
                c.addView(t)
            }
        }

        val chips = LinearLayout(ctx)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.setPadding(0, ctx.dp(10), 0, 0)
        chips.addView(chip(ctx, "${e.wakeTotal} wake locks", Pal.SKY))
        val lp = LinearLayout.LayoutParams(WRAP, WRAP)
        lp.leftMargin = ctx.dp(8)
        chips.addView(chip(ctx, "${e.wakeUnusual} unusual", if (e.wakeUnusual > 0) Pal.CORAL else Pal.MINT), lp)
        c.addView(chips)

        if (e.time in expanded) {
            fun head(text: String) {
                val t = label(ctx, text, 12f, Pal.SKY, bold = true)
                t.setPadding(0, ctx.dp(12), 0, ctx.dp(2))
                c.addView(t)
            }
            if (e.wakes.isNotEmpty()) {
                head("Wake locks")
                for (w in e.wakes) {
                    val held = if (w.heldMs >= 0) formatHeld(w.heldMs) else "unknown"
                    c.addView(label(ctx, "${w.owner} · '${w.tag}' · $held", 12f, if (w.unusual) Pal.CORAL else Pal.INK))
                }
            }
            if (e.consumers.isNotEmpty()) {
                head("Top consumers")
                for (x in e.consumers) c.addView(label(ctx, "${x.label} · ${"%.1f".format(x.mah)} mAh", 12f))
            }
            if (e.alarms.isNotEmpty()) {
                head("Alarm wakeups")
                for (a in e.alarms) c.addView(label(ctx, "${a.owner} · ${a.wakeups}", 12f))
            }
            if (e.kernel.isNotEmpty()) {
                head("Kernel wakelocks")
                for (k in e.kernel) c.addView(label(ctx, "${k.name} · ${k.count}× · ${k.duration}", 12f))
            }
        } else {
            val tap = label(ctx, "Tap for details", 11f, Pal.DIM)
            tap.setPadding(0, ctx.dp(8), 0, 0)
            c.addView(tap)
        }
        addBlock(c)
    }

    addBlock(pill(ctx, "Clear history") { onClear() })
}
