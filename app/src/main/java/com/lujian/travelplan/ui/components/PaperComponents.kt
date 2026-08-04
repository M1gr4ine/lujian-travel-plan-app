package com.lujian.travelplan.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Ink
import com.lujian.travelplan.ui.theme.Paper

@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    background: Color = Paper,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(18.dp))
            .border(2.dp, Ink, RoundedCornerShape(18.dp))
            .padding(contentPadding),
        content = content,
    )
}

fun Modifier.dashedPaperBorder(): Modifier = drawBehind {
    drawRoundRect(
        color = Ink,
        cornerRadius = CornerRadius(18.dp.toPx()),
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx())),
        ),
    )
}

@Composable
fun CoralButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, Ink),
        colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
