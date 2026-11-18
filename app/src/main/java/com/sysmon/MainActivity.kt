package com.sysmon

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var info: TextView
    private lateinit var gCpu: GraphView
    private lateinit var gTemp: GraphView
    private lateinit var gRam: GraphView

    private val refresh = object : Runnable {
        override fun run() {
            val hs = Stats.history.toList()
            gCpu.data = hs.map { it.cpu }; gTemp.data = hs.map { it.temp }; gRam.data = hs.map { it.ram }
            gCpu.invalidate(); gTemp.invalidate(); gRam.invalidate()
            info.text = if (hs.isEmpty()) "Belum ada data. Tekan MULAI." else "${hs.size} sampel (~${hs.size * 2 / 60} menit) • sumber suhu: ${Stats.tempSource}"
            h.postDelayed(this, 2000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Stats.load(this)
        val d = resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(24))
        }
        fun add(v: android.view.View) = root.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

        add(TextView(this).apply { text = "SysMonitor"; textSize = 24f; setTextColor(0xFFFFFFFF.toInt()) })
        info = TextView(this).apply { setTextColor(0xFFAAAAAA.toInt()) }; add(info)
        gCpu = GraphView(this, "CPU", 0xFF4FC3F7.toInt(), true, "%"); add(gCpu)
        gTemp = GraphView(this, "Suhu", 0xFFFF7043.toInt(), false, "°C"); add(gTemp)
        gRam = GraphView(this, "RAM", 0xFF81C784.toInt(), true, "%"); add(gRam)
        add(Button(this).apply { text = "MULAI (floating + background)"; setOnClickListener { start() } })
        add(Button(this).apply { text = "STOP"; setOnClickListener { stopService(Intent(this@MainActivity, MonitorService::class.java)) } })
        add(Button(this).apply { text = "Hapus riwayat"; setOnClickListener { Stats.clear(this@MainActivity) } })

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF121212.toInt()); addView(root) })
    }

    private fun start() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan 'Tampil di atas aplikasi lain', lalu tekan MULAI lagi", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        startForegroundService(Intent(this, MonitorService::class.java))
    }

    override fun onResume() { super.onResume(); h.post(refresh) }
    override fun onPause() { h.removeCallbacks(refresh); super.onPause() }
}
