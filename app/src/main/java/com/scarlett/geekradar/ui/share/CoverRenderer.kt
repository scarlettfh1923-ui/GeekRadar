package com.scarlett.geekradar.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import com.scarlett.geekradar.core.CoverLayout
import java.io.File
import java.io.FileOutputStream

/**
 * 应用内生成 3:4 大字封面（1080×1440，暖白底 + 单一蓝色强调），按 v1.1 样例风格绘制。
 * 只使用标题、正文首句与标签，不含任何原作者信息。文件只写入应用缓存目录（不存相册）。
 */
object CoverRenderer {
    const val W = 1080
    const val H = 1440
    private const val PAD_X = 104f
    private const val PAD_TOP = 112f
    private const val PAD_BOTTOM = 112f

    private val BG = Color.parseColor("#F7F4EC")
    private val INK = Color.parseColor("#1B1C20")
    private val BLUE = Color.parseColor("#3A64D4")
    private val NUM = Color.parseColor("#B3AFA5")
    private val SUB = Color.parseColor("#6D6C68")
    private val FOOT = Color.parseColor("#8B897F")
    private val FOOT_LINE = Color.parseColor("#DDD8CC")
    private val BAR = Color.parseColor("#ECE8DE")
    private val LINE = Color.parseColor("#E6E2D8")

    private fun bold(): Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 800, false) else Typeface.DEFAULT_BOLD

    fun render(c: CoverLayout.Content): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(BG)
        val contentW = W - PAD_X * 2

        // 顶部：栏目名 + 编号
        val top = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; typeface = Typeface.DEFAULT_BOLD; color = BLUE; letterSpacing = 0.1f }
        val topBase = PAD_TOP + 40f
        cv.drawText(c.kicker, PAD_X, topBase, top)
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; typeface = Typeface.MONOSPACE; color = NUM; letterSpacing = 0.1f }
        cv.drawText(c.number, W - PAD_X - num.measureText(c.number), topBase, num)

        // 大字标题：自动选字号（最多 4 行），最后一行蓝色
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = bold(); letterSpacing = 0.02f }
        val sizes = listOf(184f, 168f, 152f, 140f, 128f, 116f, 104f, 96f, 88f)
        val (size, lines) = CoverLayout.fitTitle(c.title, contentW, sizes, 4) { s, sz -> tp.textSize = sz; tp.measureText(s) }
        tp.textSize = size
        val lineH = size * 1.14f
        var y = topBase + 88f + size * 0.92f
        lines.forEachIndexed { i, l ->
            tp.color = if (lines.size >= 2 && i == lines.lastIndex) BLUE else INK
            cv.drawText(l, PAD_X, y, tp)
            if (i < lines.lastIndex) y += lineH
        }

        // 副标题（正文首句），最多 2 行
        val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 46f; color = SUB }
        var subBottom = y
        if (c.subtitle.isNotBlank()) {
            var sl = CoverLayout.wrap(c.subtitle, contentW) { sp.measureText(it) }
            if (sl.size > 2) sl = listOf(sl[0], sl[1].dropLast(1) + "…")
            var sy = y + 48f + 46f
            sl.forEach { cv.drawText(it, PAD_X, sy, sp); subBottom = sy; sy += 74f }
        }

        // 底部：分隔线 + 标签 / 落款
        val footBase = H - PAD_BOTTOM
        val fp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; color = FOOT }
        val lineY = footBase - 36f - 36f
        val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FOOT_LINE; strokeWidth = 3f }
        cv.drawLine(PAD_X, lineY, W - PAD_X, lineY, lp)
        cv.drawText(c.footerLeft, PAD_X, footBase, fp)
        cv.drawText(c.footerRight, W - PAD_X - fp.measureText(c.footerRight), footBase, fp)

        // 浏览器窗口插画（空间不足时省略）
        val winH = 240f
        val winBottom = lineY - 68f
        val winTop = winBottom - winH
        if (winTop > subBottom + 60f) drawWindow(cv, RectF(PAD_X, winTop, W - PAD_X, winBottom))
        return bmp
    }

    private fun drawWindow(cv: Canvas, r: RectF) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = INK }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        cv.drawRoundRect(r, 32f, 32f, fill)
        cv.drawRoundRect(r, 32f, 32f, stroke)
        val barB = r.top + 56f
        cv.drawLine(r.left, barB, r.right, barB, stroke)
        val dot = Paint(stroke).apply { strokeWidth = 4f }
        for (i in 0..2) cv.drawCircle(r.left + 34f + i * 32f, r.top + 28f, 9f, dot)
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BAR }
        cv.drawRoundRect(RectF(r.left + 150f, r.top + 18f, r.right - 24f, r.top + 38f), 10f, 10f, bar)
        val ln = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LINE }
        val inner = r.width() - 72f
        cv.drawRoundRect(RectF(r.left + 36f, barB + 32f, r.left + 36f + inner * 0.72f, barB + 50f), 9f, 9f, ln)
        cv.drawRoundRect(RectF(r.left + 36f, barB + 72f, r.left + 36f + inner * 0.54f, barB + 90f), 9f, 9f, ln)
        val btn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE }
        val btnR = RectF(r.right - 36f - 192f, barB + 104f, r.right - 36f, barB + 148f)
        cv.drawRoundRect(btnR, 14f, 14f, btn)
        // 光标圆点 + 箭头
        val cx = btnR.left - 4f; val cy = btnR.centerY() + 6f
        cv.drawCircle(cx, cy, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK })
        val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        cv.drawLine(cx - 13f, cy, cx + 13f, cy, arrow)
        val p = Path().apply { moveTo(cx + 2f, cy - 11f); lineTo(cx + 13f, cy); lineTo(cx + 2f, cy + 11f) }
        cv.drawPath(p, arrow)
    }

    /** 渲染并写入应用缓存 covers/ 目录，返回文件。每次覆盖同一个文件，不占用相册。 */
    fun renderToCache(ctx: Context, c: CoverLayout.Content): File {
        val dir = File(ctx.cacheDir, "covers").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "geekradar-cover-${System.currentTimeMillis()}.png")
        val bmp = render(c)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
    }

    /** 设置页 / 弹层用的小预览。 */
    fun preview(c: CoverLayout.Content, width: Int = 270): Bitmap {
        val full = render(c)
        val small = Bitmap.createScaledBitmap(full, width, width * H / W, true)
        if (small != full) full.recycle()
        return small
    }
}
