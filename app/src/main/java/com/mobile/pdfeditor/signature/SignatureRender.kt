package com.mobile.pdfeditor.signature

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns drawn, typed or photographed signatures into tightly cropped, transparent bitmaps. */
object SignatureRender {
    private const val PADDING_PX = 12

    /** [strokes] in pad pixels; output is [scale]× sharper so it prints well. */
    fun fromStrokes(strokes: List<List<Pair<Float, Float>>>, color: Int, widthPx: Float, scale: Float = 3f): Bitmap? {
        val pts = strokes.flatten()
        if (pts.isEmpty()) return null
        // A signature drawn across the whole pad is shrunk a lot when placed (~160pt wide), so a
        // pad-thin line would print as a hairline. Keep the line at least ~1.2% of the signature's
        // width, which comes out around 2pt once placed.
        val span = pts.maxOf { it.first } - pts.minOf { it.first }
        val lineWidth = maxOf(widthPx, span * 0.012f)
        val half = lineWidth / 2f
        val minX = pts.minOf { it.first } - half
        val minY = pts.minOf { it.second } - half
        val maxX = pts.maxOf { it.first } + half
        val maxY = pts.maxOf { it.second } + half
        val w = ceil((maxX - minX) * scale).toInt() + PADDING_PX * 2
        val h = ceil((maxY - minY) * scale).toInt() + PADDING_PX * 2
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color or 0xFF000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = lineWidth * scale
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val canvas = Canvas(bmp)
        fun tx(x: Float) = (x - minX) * scale + PADDING_PX
        fun ty(y: Float) = (y - minY) * scale + PADDING_PX
        for (s in strokes) {
            if (s.isEmpty()) continue
            if (s.size == 1) {
                canvas.drawPoint(tx(s[0].first), ty(s[0].second), paint)
                continue
            }
            val path = Path().apply { moveTo(tx(s[0].first), ty(s[0].second)) }
            for (i in 1 until s.size) {
                val (px, py) = s[i - 1]
                val (cx, cy) = s[i]
                path.quadTo(tx(px), ty(py), tx((px + cx) / 2), ty((py + cy) / 2))
            }
            path.lineTo(tx(s.last().first), ty(s.last().second))
            canvas.drawPath(path, paint)
        }
        return bmp
    }

    /** Renders [text] in a handwriting-style font. */
    fun fromText(text: String, color: Int, typeface: Typeface = Typeface.create("cursive", Typeface.NORMAL)): Bitmap? {
        if (text.isBlank()) return null
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color or 0xFF000000.toInt()
            this.typeface = typeface
            textSize = 160f
        }
        val width = ceil(paint.measureText(text)).toInt() + PADDING_PX * 2
        val fm = paint.fontMetrics
        val height = ceil(fm.descent - fm.ascent).toInt() + PADDING_PX * 2
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, PADDING_PX.toFloat(), PADDING_PX - fm.ascent, paint)
        return bmp
    }

    /** Loads a photo of a signature on paper, scaled so its long side is at most [maxSide]. */
    fun decodePhoto(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // we read pixels back
                val long = max(info.size.width, info.size.height)
                if (long > maxSide) {
                    val s = maxSide.toFloat() / long
                    decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Keeps the dark ink from a photo, recoloured to [color], with paper made transparent,
     * then crops to the ink. Returns null if no ink was found.
     */
    fun cleanPhoto(src: Bitmap, color: Int): Bitmap? {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val rgb = color and 0xFFFFFF
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val p = px[i]
                val lum = (0.299f * (p shr 16 and 0xFF) + 0.587f * (p shr 8 and 0xFF) + 0.114f * (p and 0xFF))
                val a = inkAlpha(lum)
                px[i] = (a shl 24) or rgb
                if (a > 40) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        val cw = maxX - minX + 1
        val ch = maxY - minY + 1
        val out = Bitmap.createBitmap(cw + PADDING_PX * 2, ch + PADDING_PX * 2, Bitmap.Config.ARGB_8888)
        out.setPixels(px, minY * w + minX, w, PADDING_PX, PADDING_PX, cw, ch)
        return out
    }

    /** Opacity (0–255) for a pixel of brightness [lum]: dark ink opaque, light paper transparent. */
    fun inkAlpha(lum: Float): Int = when {
        lum <= INK_DARK -> 255
        lum >= PAPER_LIGHT -> 0
        else -> ((PAPER_LIGHT - lum) / (PAPER_LIGHT - INK_DARK) * 255f).roundToInt()
    }

    private const val INK_DARK = 90f
    private const val PAPER_LIGHT = 170f
}
