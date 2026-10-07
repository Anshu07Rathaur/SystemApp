package com.anshu.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.time.LocalDate

class SystemService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var overlay: View? = null
    private var live: (() -> Unit)? = null
    private var fg = ""
    private var last = 0L
    private var lastDeny = 0L
    private val autoHide = Runnable { hide() }

    private val unlock = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { showQuests() }
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val dt = minOf(now - last, 5000L); last = now
            if (fg in Store.blocked(this@SystemService)) {
                val b = Store.balance(this@SystemService)
                if (b > 0) {
                    Store.setBalance(this@SystemService, b - dt)
                    if (b - dt <= 0) deny()
                } else deny()
            }
            live?.invoke()
            h.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ContextCompat.registerReceiver(
            this, unlock, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        last = System.currentTimeMillis()
        h.post(tick)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val p = e.packageName?.toString() ?: return
        if (p == "com.android.systemui" || p == packageName || p.contains("inputmethod") || p.contains("keyboard")) return
        fg = p
        if (p in Store.blocked(this) && Store.balance(this) <= 0) deny()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        h.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(unlock) }
        hide()
        super.onDestroy()
    }

    private fun deny() {
        val now = System.currentTimeMillis()
        if (now - lastDeny < 3000) return
        lastDeny = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        show("[SYSTEM] ACCESS DENIED", { "Reward time khatam.\nQuest complete karo, time earn karo.\n\n" + questText() }, 4000)
    }

    private fun showQuests() = show("[SYSTEM] DAILY QUEST", { questText() }, 0)

    private fun questText(): String {
        Store.settle(this)
        val today = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        val ps = Store.quests(this).filter { it.status == "PENDING" && it.date == today }
        val bal = "Reward balance: ${Store.balance(this) / 60000} min   Penalty: -${Store.penalty(this)} min"
        if (ps.isEmpty()) return "Aaj koi pending quest nahi.\n\n$bal"
        return ps.joinToString("\n\n") {
            "▸ ${it.title}\n   Time left: ${fmt(it.deadline - now)}\n   Reward +${it.reward}m   Fail -${it.penalty}m"
        } + "\n\n$bal"
    }

    private fun hide() {
        h.removeCallbacks(autoHide)
        overlay?.let { v -> runCatching { wm.removeView(v) } }
        overlay = null; live = null
    }

    private fun show(title: String, body: () -> String, autoMs: Long) {
        hide()
        val cyan = 0xFF00E5FF.toInt()
        val head = TextView(this).apply {
            text = title; setTextColor(cyan); textSize = 20f
            typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER; setPadding(0, 0, 0, 24)
        }
        val tv = TextView(this).apply {
            setTextColor(0xFFCFF4FF.toInt()); textSize = 15f; typeface = Typeface.MONOSPACE
        }
        val btn = Button(this).apply { text = "[ ACCEPT ]"; setOnClickListener { hide() } }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            background = GradientDrawable().apply {
                setColor(0xF006121F.toInt()); setStroke(3, cyan); cornerRadius = 24f
            }
            addView(head); addView(tv); addView(btn)
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ).apply { setMargins(48, 0, 48, 0) }
        val frame = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(160, 0, 0, 0)); addView(box, lp)
        }
        val wlp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        live = { tv.text = body() }
        live?.invoke()
        runCatching { wm.addView(frame, wlp); overlay = frame }
        if (autoMs > 0) h.postDelayed(autoHide, autoMs)
    }
}
