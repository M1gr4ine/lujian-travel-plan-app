package com.lujian.travelplan.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lujian.travelplan.ui.theme.Coral
import com.lujian.travelplan.ui.theme.Gold
import com.lujian.travelplan.ui.theme.Ink
import kotlinx.coroutines.delay

@Composable
fun BrandSplash(
    reduceMotion: Boolean,
    onFinished: () -> Unit,
) {
    val route = remember { Animatable(0f) }
    val pin = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val title = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            title.animateTo(1f, tween(120))
            delay(120)
        } else {
            route.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            pin.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            title.animateTo(1f, tween(240))
            delay(180)
        }
        finished = true
        onFinished()
    }

    if (!finished) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(190.dp, 92.dp)) {
                val path = Path().apply {
                    moveTo(size.width * .08f, size.height * .72f)
                    cubicTo(
                        size.width * .28f,
                        size.height * .05f,
                        size.width * .58f,
                        size.height * .98f,
                        size.width * .82f,
                        size.height * .32f,
                    )
                }
                val measured = PathMeasure().apply { setPath(path, false) }
                val visibleRoute = Path()
                measured.getSegment(0f, measured.length * route.value, visibleRoute, true)
                drawPath(visibleRoute, Gold, style = Stroke(width = 8f))
                drawCircle(Ink, radius = 7f, center = Offset(size.width * .08f, size.height * .72f))
                drawCircle(Coral, radius = 7f * route.value, center = Offset(size.width * .82f, size.height * .32f))
            }
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = Coral,
                modifier = Modifier
                    .size(54.dp)
                    .graphicsLayer {
                        scaleX = pin.value
                        scaleY = pin.value
                        alpha = pin.value
                        translationY = (1f - pin.value) * -42f
                    },
            )
            Text(
                "旅笺",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.graphicsLayer { alpha = title.value },
            )
            Text(
                "TRAVEL NOTE",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.graphicsLayer { alpha = title.value },
            )
        }
    }
}
