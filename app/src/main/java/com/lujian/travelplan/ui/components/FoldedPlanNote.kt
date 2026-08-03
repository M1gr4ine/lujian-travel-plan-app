package com.lujian.travelplan.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper

@Stable
class FoldedNoteShape(
    private val cornerRadius: Dp = 18.dp,
    private val foldSize: Dp = 34.dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(
            foldedNotePath(
                size = size,
                cornerRadius = with(density) { cornerRadius.toPx() },
                foldSize = with(density) { foldSize.toPx() },
            ),
        )
    }
}

fun Modifier.foldedNoteDecoration(
    background: Color,
    borderColor: Color = Ink,
    borderWidth: Dp = 2.dp,
    cornerRadius: Dp = 18.dp,
    foldSize: Dp = 34.dp,
): Modifier {
    val shape = FoldedNoteShape(cornerRadius, foldSize)
    return clip(shape)
        .background(background)
        .drawWithContent {
            drawContent()
            val radiusPx = cornerRadius.toPx()
            val foldPx = foldSize.toPx()
            val borderPx = borderWidth.toPx()
            val outline = foldedNotePath(size, radiusPx, foldPx)
            val fold = Path().apply {
                moveTo(size.width - foldPx, 0f)
                lineTo(size.width - foldPx, foldPx)
                lineTo(size.width, foldPx)
                close()
            }
            drawPath(fold, color = Paper)
            drawLine(
                color = borderColor,
                start = Offset(size.width - foldPx, 0f),
                end = Offset(size.width - foldPx, foldPx),
                strokeWidth = borderPx,
            )
            drawLine(
                color = borderColor,
                start = Offset(size.width - foldPx, foldPx),
                end = Offset(size.width, foldPx),
                strokeWidth = borderPx,
            )
            drawPath(outline, color = borderColor, style = Stroke(width = borderPx))
        }
}

private fun foldedNotePath(size: Size, cornerRadius: Float, foldSize: Float): Path {
    val radius = cornerRadius.coerceAtMost(minOf(size.width, size.height) / 2f)
    val fold = foldSize.coerceIn(radius, size.width - radius)
    return Path().apply {
        moveTo(radius, 0f)
        lineTo(size.width - fold, 0f)
        lineTo(size.width, fold)
        lineTo(size.width, size.height - radius)
        quadraticTo(size.width, size.height, size.width - radius, size.height)
        lineTo(radius, size.height)
        quadraticTo(0f, size.height, 0f, size.height - radius)
        lineTo(0f, radius)
        quadraticTo(0f, 0f, radius, 0f)
        close()
    }
}
