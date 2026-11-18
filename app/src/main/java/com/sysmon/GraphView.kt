package com.sysmon

import android.content.Context
import android.graphics.*
import android.view.View

class GraphView(
    ctx: Context, private val title: String, private val color: Int,
    private val fixed100: Boolean, private val unit: String, private val fromZero: Boolean = false
) : View(ctx) {
    var data: List<Float> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (150 * d).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.style = Paint.Style.FILL; p.color = 0xFF1E1E1E.toInt()
        c.drawRoundRect(0f, 0f, w, h, 16 * d, 16 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = 0x33FFFFFF
        for (i in 1..3) c.drawLine(0f, h * i / 4, w, h * i / 4, p)

        val top = 34 * d; val bot = h - 10 * d
        if (data.size > 1) {
            var lo = 0f; var hi = 100f
            if (!fixed100) {
                lo = if (fromZero) 0f else data.min() - 2f; hi = data.max() + 2f
                if (hi - lo < 10f) hi = lo + 10f
            }
            val path = Path()
            data.forEachIndexed { i, v ->
                val x = w * i / (data.size - 1)
                val y = bot - (v - lo) / (hi - lo) * (bot - top)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            p.color = color; p.strokeWidth = 2.5f * d
            c.drawPath(path, p)
        }
        p.style = Paint.Style.FILL; p.color = Color.WHITE; p.textSize = 14 * d
        val cur = data.lastOrNull()
        val txt = if (cur != null) String.format("%s: %.1f%s   (max %.1f)", title, cur, unit, data.max())
                  else "$title: -"
        c.drawText(txt, 12 * d, 22 * d, p)
    }
}
