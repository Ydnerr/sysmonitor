package com.sysmon

// FPS game dibaca dari `dumpsys SurfaceFlinger --latency`. Butuh izin DUMP (sekali via ADB):
// adb shell pm grant com.sysmon android.permission.DUMP
object Fps {
    @Volatile var value = -1f // -1 = tidak tersedia
    @Volatile private var run = false
    private val skip = listOf("StatusBar", "NavigationBar", "ScreenDecor", "InputMethod", "Wallpaper",
        "Background for", "Bounds for", "com.sysmon", "systemui", "Dim Layer", "WindowToken", "ImeContainer", "Letterbox", "Splash")

    fun sh(vararg cmd: String): List<String>? = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readLines()
        p.waitFor()
        out
    } catch (e: Exception) { null }

    private fun findLayers(): List<String> {
        val all = sh("dumpsys", "SurfaceFlinger", "--list")?.map { it.trim() }
            ?.filter { it.isNotEmpty() && skip.none { s -> it.contains(s, true) } } ?: return emptyList()
        val sv = all.filter { it.startsWith("SurfaceView") }
        return (if (sv.isNotEmpty()) sv else all).take(5)
    }

    private fun layerFps(layer: String): Float {
        val l = sh("dumpsys", "SurfaceFlinger", "--latency", layer) ?: return -1f
        val ts = l.drop(1).mapNotNull { it.trim().split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() }
            .filter { it > 0 && it != Long.MAX_VALUE }
        if (ts.size < 2) return -1f
        val last = ts.max()
        if (System.nanoTime() - last > 1_500_000_000L) return 0f
        val r = ts.filter { last - it <= 1_000_000_000L }.sorted()
        if (r.size < 2) return 0f
        return (r.size - 1) * 1e9f / (r.last() - r.first())
    }

    fun start() {
        if (run) return
        run = true
        Thread {
            var cands = emptyList<String>(); var n = 0
            while (run) {
                try {
                    if (n++ % 5 == 0) cands = findLayers()
                    val r = cands.map { layerFps(it) }
                    value = if (r.isEmpty() || r.all { it < 0 }) -1f else r.max().coerceAtLeast(0f)
                } catch (_: Exception) { value = -1f }
                Thread.sleep(1000)
            }
        }.start()
    }

    fun stop() { run = false; value = -1f }
}
