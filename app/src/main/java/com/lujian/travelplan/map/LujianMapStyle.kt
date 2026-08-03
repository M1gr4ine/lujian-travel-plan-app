package com.lujian.travelplan.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

const val LUJIAN_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

object LujianMapStyle {
    private const val PAPER = "#FAF6EF"
    private const val PAPER_DEEP = "#EEE5D7"
    private const val INK = "#2A2520"
    private const val MUTED_INK = "#766D64"
    private const val WATER = "#BBDAD6"
    private const val GREEN = "#DDE5C9"
    private const val ROAD = "#D9B98D"
    private const val CORAL = "#FF6B4A"
    private const val VINTAGE_RED = "#B85F52"

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
        val width = (40 * density).toInt().coerceAtLeast(40)
        val height = (44 * density).toInt().coerceAtLeast(44)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scaleX = width / 40f
        val scaleY = height / 44f
        canvas.scale(scaleX, scaleY)

        canvas.drawLine(20f, 20f, 20f, 42f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(INK)
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.BUTT
        })

        canvas.drawCircle(21.2f, 14.6f, 11.3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 42, 37, 32)
            style = Paint.Style.FILL
        })
        canvas.drawCircle(20f, 13.5f, 10.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(VINTAGE_RED)
            style = Paint.Style.FILL
        })
        canvas.drawCircle(20f, 13.5f, 10.6f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(INK)
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
