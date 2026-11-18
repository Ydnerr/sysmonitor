package com.sysmon

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

data class Sample(val t: Long, val cpu: Float, val temp: Float, val ram: Float, val fps: Float = -1f)

object Stats {
    private const val MAX = 1800 // 1800 sampel x 2 detik = 1 jam
    val history = ArrayList<Sample>()
    var tempSource = "Baterai"
    private var lastTotal = 0L
    private var lastIdle = 0L

    private fun rd(p: String): String? = try { File(p).readText().trim() } catch (e: Exception) { null }

    // Cara 1: /proc/stat (diblokir Android 8+ di kebanyakan device)
    private fun cpuFromProc(): Float? {
        val l = rd("/proc/stat")?.lineSequence()?.firstOrNull() ?: return null
        val n = l.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (n.size < 5) return null
        val idle = n[3] + n[4]
        val total = n.sum()
        val first = lastTotal == 0L
        val dt = total - lastTotal
        val di = idle - lastIdle
        lastTotal = total; lastIdle = idle
        return if (!first && dt > 0) 100f * (dt - di) / dt else null
    }

    // Cara 2 (fallback): estimasi beban dari rasio frekuensi sekarang / frekuensi max tiap core
    private fun cpuFromFreq(): Float? {
        var sum = 0f; var n = 0
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val b = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val c = rd(b + "scaling_cur_freq")?.toLongOrNull() ?: continue
            val m = rd(b + "cpuinfo_max_freq")?.toLongOrNull() ?: continue
            sum += c.toFloat() / m; n++
        }
        return if (n > 0) 100f * sum / n else null
    }

    // Suhu CPU dari thermal zone (kalau tidak diblokir ROM), kalau gagal pakai suhu baterai
    private fun cpuTemp(): Float? {
        var best: Float? = null
        File("/sys/class/thermal").listFiles()?.forEach { z ->
            if (!z.name.startsWith("thermal_zone")) return@forEach
            val type = rd("${z.path}/type") ?: return@forEach
            if (!(type.contains("cpu") || type.contains("tsens"))) return@forEach
            var v = rd("${z.path}/temp")?.toFloatOrNull() ?: return@forEach
            if (v > 200) v /= 1000f
            if (v in 15f..120f && (best == null || v > best!!)) best = v
        }
        return best
    }

    fun read(c: Context): Sample {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val ram = 100f * (mi.totalMem - mi.availMem) / mi.totalMem
        val ct = cpuTemp()
        val temp: Float
        if (ct != null) {
            temp = ct; tempSource = "CPU"
        } else {
            val i = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
            tempSource = "Baterai"
        }
        val cpu = (cpuFromProc() ?: cpuFromFreq() ?: 0f).coerceIn(0f, 100f)
        return Sample(System.currentTimeMillis(), cpu, temp, ram, Fps.value)
    }

    fun add(c: Context, s: Sample) {
        history.add(s)
        if (history.size > MAX) history.removeAt(0)
        try { File(c.filesDir, "history.csv").appendText("${s.t},${s.cpu},${s.temp},${s.ram},${s.fps}\n") } catch (_: Exception) {}
    }

    fun load(c: Context) {
        if (history.isNotEmpty()) return
        try {
            val f = File(c.filesDir, "history.csv")
            val lines = f.readLines().takeLast(MAX)
            lines.forEach {
                val p = it.split(",")
                if (p.size >= 4) history.add(Sample(p[0].toLong(), p[1].toFloat(), p[2].toFloat(), p[3].toFloat(), p.getOrNull(4)?.toFloatOrNull() ?: -1f))
            }
            if (lines.isNotEmpty()) f.writeText(lines.joinToString("\n") + "\n")
        } catch (_: Exception) {}
    }

    fun clear(c: Context) {
        history.clear()
        File(c.filesDir, "history.csv").delete()
    }
}
