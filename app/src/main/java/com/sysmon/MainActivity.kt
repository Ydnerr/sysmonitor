package com.sysmon

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var info: TextView
    private lateinit var box: LinearLayout
    private lateinit var gFps: GraphView
    private lateinit var gCpu: GraphView
    private lateinit var gTemp: GraphView
    private lateinit var gRam: GraphView

    private val refresh = object : Runnable {
        override fun run() {
            val hs = Stats.history.toList()
            gFps.data = hs.map { it.fps.coerceAtLeast(0f) }
            gCpu.data = hs.map { it.cpu }; gTemp.data = hs.map { it.temp }; gRam.data = hs.map { it.ram }
            listOf(gFps, gCpu, gTemp, gRam).forEach { it.invalidate() }
            val fpsInfo = if (Fps.value >= 0) "FPS aktif" else "FPS belum tersedia (butuh izin DUMP via ADB)"
            info.text = if (hs.isEmpty()) "Belum ada data. Tekan MULAI."
                else "${hs.size} sampel (~${hs.size * 2 / 60} menit) • suhu: ${Stats.tempSource} • $fpsInfo"
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
        fun add(p: ViewGroup, v: View) = p.addView(v, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) })

        add(root, TextView(this).apply { text = "SysMonitor"; textSize = 24f; setTextColor(0xFFFFFFFF.toInt()) })
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF121212.toInt()); setPadding(dp(4), dp(4), dp(4), dp(8)) }
        info = TextView(this).apply { setTextColor(0xFFAAAAAA.toInt()) }; add(box, info)
        gFps = GraphView(this, "FPS", 0xFFFFD54F.toInt(), false, "", true); add(box, gFps)
        gCpu = GraphView(this, "CPU", 0xFF4FC3F7.toInt(), true, "%"); add(box, gCpu)
        gTemp = GraphView(this, "Suhu", 0xFFFF7043.toInt(), false, "°C"); add(box, gTemp)
        gRam = GraphView(this, "RAM", 0xFF81C784.toInt(), true, "%"); add(box, gRam)
        add(root, box)
        add(root, Button(this).apply { text = "MULAI (floating + background)"; setOnClickListener { start() } })
        add(root, Button(this).apply { text = "STOP"; setOnClickListener { stopService(Intent(this@MainActivity, MonitorService::class.java)) } })
        add(root, Button(this).apply { text = "Simpan grafik jadi PNG"; setOnClickListener { savePng() } })
        add(root, Button(this).apply { text = "Hapus riwayat"; setOnClickListener { Stats.clear(this@MainActivity) } })

        setContentView(ScrollView(this).apply { setBackgroundColor(0xFF121212.toInt()); addView(root) })
    }

    private fun savePng() {
        val bmp = Bitmap.createBitmap(box.width, box.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(0xFF121212.toInt()); box.draw(c)
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "sysmonitor_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SysMonitor")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Toast.makeText(this, "Tersimpan di Pictures/SysMonitor", Toast.LENGTH_LONG).show()
        }
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
