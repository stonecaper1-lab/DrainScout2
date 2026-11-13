package com.drain.scout

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("drainscout", MODE_PRIVATE) }

    private lateinit var column: LinearLayout
    private lateinit var statusDot: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var primaryButton: TextView
    private lateinit var baselineButton: TextView
    private lateinit var copyButton: TextView
    private lateinit var shareButton: TextView
    private val tabViews = ArrayList<TextView>()

    private var built = false
    private var tab = 0
    private var hasPermission = false
    private var loading = false
    private var error: String? = null
    private var notice: String? = null
    private var snapshot: Snapshot? = null
    private var baselineLevel: Int? = null
    private var baselineTime: Long? = null
    private var updateAlerts = true

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            runOnUiThread {
                refreshPermission()
                if (grantResult != PackageManager.PERMISSION_GRANTED) toast("Shizuku permission denied")
            }
        }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { refreshPermission() }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { refreshPermission() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shell.init(this)
        edgeToEdge()
        updateAlerts = UpdatePrefs.enabled(this)
        val level = prefs.getInt("ref_level", -1)
        val time = prefs.getLong("ref_time", 0L)
        if (level >= 0 && time > 0L) {
            baselineLevel = level
            baselineTime = time
        }
        buildUi()

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        if (updateAlerts) {
            UpdateScheduler.ensure(this)
            if (!UpdatePrefs.askedPermission(this)) {
                UpdatePrefs.setAskedPermission(this)
                askNotifications()
            }
        }
        hasPermission = DiagnosticEngine.hasShellPermission()
        built = true
        render()
    }

    override fun onResume() {
        super.onResume()
        if (built) refreshPermission()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ window

    @Suppress("DEPRECATION")
    private fun edgeToEdge() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    @Suppress("DEPRECATION")
    private fun applyInsets(insets: WindowInsets) {
        val l: Int
        val t: Int
        val r: Int
        val b: Int
        if (Build.VERSION.SDK_INT >= 30) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            l = i.left
            t = i.top
            r = i.right
            b = i.bottom
        } else {
            l = insets.systemWindowInsetLeft
            t = insets.systemWindowInsetTop
            r = insets.systemWindowInsetRight
            b = insets.systemWindowInsetBottom
        }
        column.setPadding(l, t, r, b)
    }

    // ------------------------------------------------------------------ layout

    private fun buildUi() {
        val root = FrameLayout(this)
        root.addView(GlowView(this), FrameLayout.LayoutParams(MATCH, MATCH))

        column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        root.setOnApplyWindowInsetsListener { _, insets ->
            applyInsets(insets)
            insets
        }

        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(22), dp(14), dp(22), dp(10))
        header.addView(label(this, "DrainScout", 30f, Pal.INK, bold = true))
        val statusRow = LinearLayout(this)
        statusRow.orientation = LinearLayout.HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        statusDot = LinearLayout(this)
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(8), dp(8)))
        statusText = label(this, "", 13f, Pal.DIM)
        statusText.setPadding(dp(8), 0, 0, 0)
        statusRow.addView(statusText)
        header.addView(statusRow)
        column.addView(header, LinearLayout.LayoutParams(MATCH, WRAP))

        val buttons = LinearLayout(this)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.setPadding(dp(12), 0, dp(12), dp(4))
        primaryButton = pill(this, "Capture", primary = true, sp = 13f, hPadDp = 6) { onPrimary() }
        baselineButton = pill(this, "Baseline", sp = 13f, hPadDp = 6) { setBaseline() }
        copyButton = pill(this, "Copy", sp = 13f, hPadDp = 6) { copyReport() }
        shareButton = pill(this, "Share", sp = 13f, hPadDp = 6) { shareReport() }
        addButton(buttons, primaryButton, 1.3f)
        addButton(buttons, baselineButton, 1f)
        addButton(buttons, copyButton, 0.9f)
        addButton(buttons, shareButton, 0.9f)
        column.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))

        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(8), dp(16), dp(16))
        scroll.addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
        column.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))

        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.HORIZONTAL
        TABS.forEachIndexed { index, name ->
            val tv = TextView(this)
            tv.text = name
            tv.gravity = Gravity.CENTER
            tv.maxLines = 1
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            tv.setPadding(dp(12), dp(11), dp(12), dp(11))
            tv.isClickable = true
            tv.setOnClickListener { selectTab(index) }
            tabViews.add(tv)
            inner.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP, 1f))
        }
        val bar = HorizontalScrollView(this)
        bar.isHorizontalScrollBarEnabled = false
        bar.isFillViewport = true
        bar.overScrollMode = View.OVER_SCROLL_NEVER
        bar.background = glass(this, 28, strong = true)
        bar.setPadding(dp(4), dp(4), dp(4), dp(4))
        bar.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
        val barParams = LinearLayout.LayoutParams(MATCH, WRAP)
        barParams.setMargins(dp(14), dp(6), dp(14), dp(12))
        column.addView(bar, barParams)

        setContentView(root)
    }

    private fun addButton(row: LinearLayout, button: TextView, weight: Float) {
        val lp = LinearLayout.LayoutParams(0, WRAP, weight)
        lp.setMargins(dp(4), 0, dp(4), 0)
        row.addView(button, lp)
    }

    // ------------------------------------------------------------------ rendering

    private fun currentDrain(): Drain? {
        val s = snapshot ?: return null
        val level = s.battery?.level ?: return null
        val refLevel = baselineLevel ?: return null
        val refTime = baselineTime ?: return null
        return drainSince(refLevel, refTime, level, s.capturedAt)
    }

    private fun render() {
        val dot = GradientDrawable()
        dot.shape = GradientDrawable.OVAL
        dot.setColor(if (hasPermission) Pal.MINT else Pal.AMBER)
        statusDot.background = dot
        statusText.text = if (hasPermission) "Shizuku connected" else "Shizuku permission needed"

        primaryButton.text = when {
            loading -> "Capturing…"
            !hasPermission -> "Connect"
            else -> "Capture"
        }
        setLook(primaryButton, !loading)
        val has = snapshot != null
        setLook(baselineButton, has)
        setLook(copyButton, has)
        setLook(shareButton, has)

        tabViews.forEachIndexed { index, tv ->
            val on = index == tab
            tv.setTextColor(if (on) Pal.INK else Pal.DIM)
            tv.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            if (on) {
                val g = GradientDrawable()
                g.cornerRadius = dp(22).toFloat()
                g.setColor(withAlpha(0x4D, Pal.MINT))
                tv.background = g
            } else {
                tv.background = null
            }
        }

        content.removeAllViews()
        error?.let { content.addBlock(label(this, it, 13f, Pal.CORAL)) }
        notice?.let { content.addBlock(label(this, it, 12f, Pal.AMBER)) }
        baselineTime?.let {
            content.addBlock(label(this, "Baseline: ${baselineLevel}% at ${formatTime(it)}", 12f, Pal.DIM), 8)
        }

        val s = snapshot
        when (tab) {
            3 -> content.buildDevice { render() }
            5 -> content.buildAbout(updateAlerts, { toggleUpdates() }, { openUrl(it) })
            else -> if (s == null) {
                content.addBlock(
                    emptyCard(
                        this,
                        "No capture yet. Connect Shizuku, then tap Capture. The first capture becomes your baseline. Capture again later to see the drain rate."
                    )
                )
            } else {
                when (tab) {
                    0 -> content.buildOverview(s, currentDrain())
                    1 -> content.buildWakelocks(s)
                    2 -> content.buildAlarms(s)
                    else -> content.buildReportView(buildReport(s, currentDrain()))
                }
            }
        }
    }

    private fun setLook(v: View, enabled: Boolean) {
        v.isEnabled = enabled
        v.alpha = if (enabled) 1f else 0.45f
    }

    private fun selectTab(index: Int) {
        tab = index
        render()
        scroll.post { scroll.scrollTo(0, 0) }
    }

    // ------------------------------------------------------------------ actions

    private fun refreshPermission() {
        hasPermission = DiagnosticEngine.hasShellPermission()
        render()
    }

    private fun onPrimary() {
        if (!hasPermission) requestShizuku() else capture()
    }

    private fun capture() {
        if (loading) return
        if (!DiagnosticEngine.hasShellPermission()) {
            hasPermission = false
            error = "Shizuku permission is required. Start Shizuku, then tap Connect."
            render()
            return
        }
        loading = true
        error = null
        notice = null
        render()
        val appContext = applicationContext
        Thread {
            var captured: Snapshot? = null
            var failed: String? = null
            try {
                captured = DiagnosticEngine.capture(appContext)
            } catch (t: Throwable) {
                failed = t.message ?: t.javaClass.simpleName
            }
            val snap = captured
            val failure = failed
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loading = false
                hasPermission = true
                val level = snap?.battery?.level
                if (snap != null) {
                    snapshot = snap
                    if (level != null && baselineLevel == null) saveBaseline(level, snap.capturedAt)
                }
                error = when {
                    failure != null -> failure
                    level == null -> "No data came back. ${Shell.lastError ?: "Is Shizuku still running?"} (channel: ${Shell.mode})"
                    else -> null
                }
                notice = if (failure == null && level != null && Shell.lastError != null) {
                    "Note: ${Shell.lastError} (using ${Shell.mode})"
                } else {
                    null
                }
                render()
            }
        }.start()
    }

    private fun setBaseline() {
        val s = snapshot ?: return
        val level = s.battery?.level ?: return
        saveBaseline(level, s.capturedAt)
        render()
    }

    private fun saveBaseline(level: Int, time: Long) {
        baselineLevel = level
        baselineTime = time
        prefs.edit().putInt("ref_level", level).putLong("ref_time", time).apply()
    }

    private fun reportText(): String {
        val s = snapshot ?: return ""
        return buildReport(s, currentDrain())
    }

    private fun copyReport() {
        val text = reportText()
        if (text.isEmpty()) return
        val cm = getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("DrainScout report", text))
        toast("Report copied")
    }

    private fun shareReport() {
        val text = reportText()
        if (text.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND)
        send.type = "text/plain"
        send.putExtra(Intent.EXTRA_SUBJECT, "DrainScout report")
        send.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Share report"))
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Throwable) {
            toast("No app can open this link")
        }
    }

    private fun toggleUpdates() {
        updateAlerts = !updateAlerts
        UpdatePrefs.setEnabled(this, updateAlerts)
        UpdateScheduler.sync(this, updateAlerts)
        if (updateAlerts) askNotifications()
        render()
    }

    private fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                toast("Start Shizuku first, then try again")
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                refreshPermission()
                return
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                toast("Permission was denied permanently. Allow DrainScout inside the Shizuku app.")
                return
            }
            Shizuku.requestPermission(4001)
        } catch (e: Exception) {
            toast("Shizuku unavailable: ${e.message}")
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        val TABS = listOf("Overview", "Wakelocks", "Alarms", "Device", "Report", "About")
    }
}
