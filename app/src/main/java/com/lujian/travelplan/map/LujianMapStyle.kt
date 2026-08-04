package com.lujian.travelplan.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.lujian.travelplan.R
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.SymbolLayer

object LujianMapStyle {
    private const val PAPER = "#FAF6EF"
    private const val PAPER_DEEP = "#EEE5D7"
    private const val INK = "#2A2520"
    private const val MUTED_INK = "#766D64"
    private const val WATER = "#BBDAD6"
    private const val GREEN = "#DDE5C9"
    private const val ROAD = "#D9B98D"
    private const val CORAL = "#FF6B4A"

    const val USES_REMOTE_STYLE_DOCUMENT = false

    @Volatile
    private var cachedStyleJson: String? = null

    /** 内置旧版 Positron 样式定义，保留原视觉并省去首屏远程样式请求。 */
    fun styleBuilder(context: Context): Style.Builder {
        val json = cachedStyleJson ?: synchronized(this) {
            cachedStyleJson ?: context.resources.openRawResource(R.raw.lujian_positron_style)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
                .also { cachedStyleJson = it }
        }
        return Style.Builder().fromJson(json)
    }

    fun apply(style: Style) {
        style.layers.forEach { layer ->
            val id = layer.id.lowercase()
            when (layer) {
                is BackgroundLayer -> layer.setProperties(backgroundColor(PAPER))
                is FillLayer -> layer.setProperties(
                    fillColor(
                        when {
                            "water" in id -> WATER
                            "park" in id || "wood" in id || "grass" in id || "landcover" in id -> GREEN
                            "building" in id -> PAPER_DEEP
                            else -> PAPER
                        },
                    ),
                    fillOpacity(if ("building" in id) 0.78f else 1f),
                )
                is LineLayer -> layer.setProperties(
                    lineColor(
                        when {
                            "water" in id -> "#83BDB7"
                            "road" in id || "highway" in id || "street" in id -> ROAD
                            "boundary" in id || "admin" in id -> MUTED_INK
                            "rail" in id -> CORAL
                            else -> "#CFC4B5"
                        },
                    ),
                    lineOpacity(if ("road" in id || "highway" in id) 0.88f else 0.72f),
                )
                is SymbolLayer -> layer.setProperties(
                    textColor(if ("country" in id || "city" in id || "place" in id) INK else MUTED_INK),
                    textHaloColor(PAPER),
                    textHaloWidth(1.35f),
                )
            }
        }
    }

    fun createPin(context: Context): Icon {
        val density = context.resources.displayMetrics.density
        val width = (LujianPinVisual.WIDTH * density).toInt().coerceAtLeast(LujianPinVisual.WIDTH.toInt())
        val height = (LujianPinVisual.HEIGHT * density).toInt().coerceAtLeast(LujianPinVisual.HEIGHT.toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scaleX = width / LujianPinVisual.WIDTH
        val scaleY = height / LujianPinVisual.HEIGHT
        canvas.scale(scaleX, scaleY)

        canvas.drawLine(LujianPinVisual.CENTER_X, 20f, LujianPinVisual.CENTER_X, LujianPinVisual.STEM_BOTTOM_Y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(LujianPinVisual.INK)
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.BUTT
        })

        canvas.drawCircle(21.2f, 14.6f, 11.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 42, 37, 32)
            style = Paint.Style.FILL
        })
        canvas.drawCircle(LujianPinVisual.CENTER_X, LujianPinVisual.HEAD_CENTER_Y, LujianPinVisual.HEAD_RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(LujianPinVisual.VINTAGE_RED)
            style = Paint.Style.FILL
        })
        canvas.drawCircle(LujianPinVisual.CENTER_X, LujianPinVisual.HEAD_CENTER_Y, LujianPinVisual.HEAD_RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(LujianPinVisual.INK)
            style = Paint.Style.STROKE
            strokeWidth = 2.1f
        })
        canvas.drawCircle(16.8f, 10.2f, 2.4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 250, 246, 239)
            style = Paint.Style.FILL
        })
        return IconFactory.getInstance(context).fromBitmap(bitmap)
    }
}
