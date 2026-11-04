package com.easypt.gui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 壁纸 + 玻璃取样资源。
 *
 * Android 11 (API 30) 上 RenderEffect / AGSL 都不可用，backdrop 库的 blur/lens 会静默跳过，
 * 所以这里预先烘焙若干级模糊位图，运行时按控件在屏幕上的位置取样绘制 —— 等效的实时玻璃。
 */
class GlassAssets(
    val full: ImageBitmap,
    /** 半分辨率模糊图，index 越大越模糊 */
    val levels: List<ImageBitmap>,
    val topLum: Float,
    val bottomLum: Float,
    /** 全图平均亮度：浅壁纸要加深玻璃底，否则白字看不清 */
    val avgLum: Float,
    val width: Int,
    val height: Int
) {
    fun level(t: Float): ImageBitmap {
        val i = (t.coerceIn(0f, 1f) * (levels.size - 1)).roundToInt()
        return levels[i]
    }
}

object Wallpaper {

    private const val FILE = "wallpaper.img"

    /** 生成默认壁纸：深色底 + 高饱和光斑，保证玻璃折射看得出来 */
    fun default(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF0A1020.toInt())
        val blobs = listOf(
            floatArrayOf(0.16f, 0.10f, 0.62f),
            floatArrayOf(0.88f, 0.20f, 0.58f),
            floatArrayOf(0.48f, 0.46f, 0.62f),
            floatArrayOf(0.05f, 0.78f, 0.60f),
            floatArrayOf(0.92f, 0.86f, 0.55f),
            floatArrayOf(0.55f, 0.97f, 0.45f)
        )
        val colors = intArrayOf(
            0xFF2F6BFF.toInt(),
            0xFF7A3CF0.toInt(),
            0xFF00C6A8.toInt(),
            0xFFFF5E3A.toInt(),
            0xFF2D6BFF.toInt(),
            0xFF00A2FF.toInt()
        )
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in blobs.indices) {
            val b = blobs[i]
            p.shader = RadialGradient(
                w * b[0], h * b[1], max(w, h) * b[2],
                colors[i], 0x00000000, Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        p.shader = null
        return bmp
    }

    /** 等比铺满并压暗，输出恰好屏幕尺寸，保证背景图与玻璃取样像素级对齐 */
    fun prepare(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val cw = src.width.toFloat()
        val ch = src.height.toFloat()
        val s = max(w / cw, h / ch)
        val dw = cw * s
        val dh = ch * s
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        c.drawBitmap(src, Rect(0, 0, src.width, src.height), RectF(left, top, left + dw, top + dh), p)
        c.drawColor(0x46000000) // 压暗，保证白字可读
        return out
    }

    fun build(full: Bitmap): GlassAssets {
        val hw = max(1, full.width / 2)
        val hh = max(1, full.height / 2)
        val half = Bitmap.createScaledBitmap(full, hw, hh, true)
        val levels = listOf(
            blur(half, 2),
            blur(half, 5),
            blur(half, 11)
        ).map { it.asImageBitmap() }
        return GlassAssets(
            full = full.asImageBitmap(),
            levels = levels,
            topLum = luminance(full, 0, max(1, full.height / 8)),
            bottomLum = luminance(full, full.height - max(1, full.height / 8), full.height),
            avgLum = luminance(full, 0, full.height),
            width = full.width,
            height = full.height
        )
    }

    fun load(ctx: Context): Bitmap? {
        val f = File(ctx.filesDir, FILE)
        if (!f.exists() || f.length() < 8) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (_: Throwable) {
            null
        }
    }

    fun save(ctx: Context, uri: Uri): Boolean = try {
        val f = File(ctx.filesDir, FILE)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { out -> input.copyTo(out) }
        }
        true
    } catch (_: Throwable) {
        false
    }

    fun clear(ctx: Context) {
        runCatching { File(ctx.filesDir, FILE).delete() }
    }

    // ---------------- 内部 ----------------

    private fun luminance(bmp: Bitmap, y0: Int, y1: Int): Float {
        val w = bmp.width
        val step = max(1, w / 32)
        var sum = 0.0
        var n = 0
        val stepY = max(1, (y1 - y0) / 16)
        var y = y0
        while (y < y1) {
            var x = 0
            while (x < w) {
                val p = bmp.getPixel(x, y)
                sum += (0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF))
                n++
                x += step
            }
            y += stepY
        }
        return if (n == 0) 0f else (sum / n / 255.0).toFloat()
    }

    private fun blur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        var px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        px = passH(px, w, h, radius)
        px = passV(px, w, h, radius)
        px = passH(px, w, h, radius)
        px = passV(px, w, h, radius)
        px = saturate(px, 1.45f)   // 参考项目的 vibrancy：提饱和度，玻璃才“透亮”
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** 饱和度提升，亮度按感知权重保持 */
    private fun saturate(px: IntArray, s: Float): IntArray {
        val a00 = 0.213f + 0.787f * s
        val a01 = 0.715f - 0.715f * s
        val a02 = 0.072f - 0.072f * s
        val a10 = 0.213f - 0.213f * s
        val a11 = 0.715f + 0.285f * s
        val a12 = 0.072f - 0.072f * s
        val a20 = 0.213f - 0.213f * s
        val a21 = 0.715f - 0.715f * s
        val a22 = 0.072f + 0.928f * s
        for (i in px.indices) {
            val p = px[i]
            val al = (p ushr 24) and 0xFF
            val r = ((p shr 16) and 0xFF).toFloat()
            val g = ((p shr 8) and 0xFF).toFloat()
            val b = (p and 0xFF).toFloat()
            val nr = (r * a00 + g * a01 + b * a02).toInt().coerceIn(0, 255)
            val ng = (r * a10 + g * a11 + b * a12).toInt().coerceIn(0, 255)
            val nb = (r * a20 + g * a21 + b * a22).toInt().coerceIn(0, 255)
            px[i] = (al shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        return px
    }

    private fun passH(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val dst = IntArray(w * h)
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var a = 0
            var red = 0
            var g = 0
            var b = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                a += (p ushr 24) and 0xFF
                red += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (x in 0 until w) {
                dst[row + x] = ((a / div) shl 24) or ((red / div) shl 16) or ((g / div) shl 8) or (b / div)
                val add = src[row + (x + r + 1).coerceIn(0, w - 1)]
                val sub = src[row + (x - r).coerceIn(0, w - 1)]
                a += ((add ushr 24) and 0xFF) - ((sub ushr 24) and 0xFF)
                red += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
        return dst
    }

    private fun passV(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val dst = IntArray(w * h)
        val div = 2 * r + 1
        for (x in 0 until w) {
            var a = 0
            var red = 0
            var g = 0
            var b = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                a += (p ushr 24) and 0xFF
                red += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (y in 0 until h) {
                dst[y * w + x] = ((a / div) shl 24) or ((red / div) shl 16) or ((g / div) shl 8) or (b / div)
                val add = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                val sub = src[(y - r).coerceIn(0, h - 1) * w + x]
                a += ((add ushr 24) and 0xFF) - ((sub ushr 24) and 0xFF)
                red += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
        return dst
    }
}
