package com.sysmon

// Total pemakaian CPU asli dari `dumpsys cpuinfo` (butuh izin DUMP, sama seperti FPS).
// Dipoll tiap 5 detik karena sistem baru menghitung ulang kalau jarak antar dump >= 5 detik.
object CpuInfo {
    @Volatile var value = -1f // -1 = tidak tersedia
    @Volatile private var run = false
    private val re = Regex("""(\d+(?:\.\d+)?)%\s+TOTAL""")

    fun start() {
        if (run) return
        run = true
        Thread {
            while (run) {
                val out = Fps.sh("dumpsys", "cpuinfo")
                val m = out?.firstNotNullOfOrNull { re.find(it) }
                value = m?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
                Thread.sleep(5000)
            }
        }.start()
    }

    fun stop() { run = false; value = -1f }
}
