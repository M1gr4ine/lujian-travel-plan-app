package com.lujian.travelplan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.lujian.travelplan.map.LujianPinVisual
import kotlin.math.min

@Composable
fun LujianPinMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val factor = min(size.width / LujianPinVisual.WIDTH, size.height / LujianPinVisual.HEIGHT)
        val left = (size.width - LujianPinVisual.WIDTH * factor) / 2f
        val top = (size.height - LujianPinVisual.HEIGHT * factor) / 2f
        translate(left, top) {
            scale(factor, pivot = Offset.Zero) {
                drawLine(
                    color = Color(0xFF2A2520),
                    start = Offset(LujianPinVisual.CENTER_X, 20f),
                    end = Offset(LujianPinVisual.CENTER_X, LujianPinVisual.STEM_BOTTOM_Y),
                    strokeWidth = 1.8f,
                    cap = StrokeCap.Butt,
                )
                drawCircle(Color(0x2A2A2520), 11.3f, Offset(21.2f, 14.6f))
                drawCircle(
                    color = Color(0xFFB85F52),
                    radius = LujianPinVisual.HEAD_RADIUS,
                    center = Offset(LujianPinVisual.CENTER_X, LujianPinVisual.HEAD_CENTER_Y),
                )
                drawCircle(
                    color = Color(0xFF2A2520),
                    radius = LujianPinVisual.HEAD_RADIUS,
                    center = Offset(LujianPinVisual.CENTER_X, LujianPinVisual.HEAD_CENTER_Y),
                    style = Stroke(width = 2.1f),
                )
                drawCircle(Color(0xB4FAF6EF), 2.4f, Offset(16.8f, 10.2f))
            }
        }
    }
}
