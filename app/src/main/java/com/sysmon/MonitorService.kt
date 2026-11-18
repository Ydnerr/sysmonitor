package com.sysmon

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.*
import android.widget.TextView

class MonitorService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var tv: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            val s = Stats.read(this@MonitorService)
            Stats.add(this@MonitorService, s)
            val f = if (s.fps >= 0) String.format("%.0f", s.fps) else "--"
            tv?.text = String.format("FPS %s\nCPU %.0f%%\n%s %.1f°C\nRAM %.0f%%", f, s.cpu, Stats.tempSource, s.temp, s.ram)
            h.postDelayed(this, 2000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Stats.load(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mon", "Monitor", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "mon")
            .setContentTitle("SysMonitor aktif")
            .setSmallIcon(android.R.drawable.ic_menu_info_details).build())
        showOverlay()
        Fps.start()
        CpuInfo.start()
        h.post(tick)
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w
        val v = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 11f
            setPadding(16, 8, 16, 8); setBackgroundColor(0x99000000.toInt())
        }
        val lp = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT, TYPE_APPLICATION_OVERLAY,
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL or FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 120 }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt()
                    w.updateViewLayout(v, lp)
                }
            }
            true
        }
        w.addView(v, lp)
        tv = v
    }

    override fun onDestroy() {
        h.removeCallbacks(tick)
        Fps.stop()
        CpuInfo.stop()
        tv?.let { wm?.removeView(it) }
        super.onDestroy()
    }
}
