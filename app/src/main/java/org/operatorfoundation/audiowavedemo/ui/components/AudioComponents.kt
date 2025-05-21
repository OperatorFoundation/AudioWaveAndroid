package org.operatorfoundation.audiowavedemo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun AudioLevelIndicator(
    level: Float,
    modifier: Modifier = Modifier
) {
    LinearProgressIndicator(
        progress = { level },
        modifier = modifier,
        color = when {
            level < 0.3f -> Color.Green
            level < 0.7f -> Color.Yellow
            else -> Color.Red
        }
    )
}

@Composable
fun AudioVisualizer(
    audioLevel: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Animate audio bars when active
    val animatedLevel by animateFloatAsState(
        targetValue = if (isActive) audioLevel else 0f,
        animationSpec = tween(300)
    )

    // Create a sine wave animation for when there's no input
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnimation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave phase"
    )

    Canvas(modifier = modifier.fillMaxSize())
    {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (isActive)
        {
            // When active, draw actual visualization

            // Waveform
            val path = Path()
            path.moveTo(0f, centerY)

            val numPoints = 100
            for (i in 0..numPoints) {
                val x = width * i / numPoints

                // Generate a waveform with varying amplitude based on the audio level
                val amplitude = height * 0.4f * animatedLevel

                // Use a combination of sines to make it more interesting
                val y = centerY + amplitude * sin(i * 0.1f + phase) *
                        sin(i * 0.05f + phase * 0.7f)

                path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = Color.Green,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Visualize bars
            val barCount = 20
            val barWidth = width / barCount - 2

            for (i in 0 until barCount)
            {
                val barHeight = audioLevel * height * 0.8f *
                        (0.3f + 0.7f * abs(sin((i / barCount.toFloat()) * PI.toFloat() + phase)))

                drawRect(
                    color = Color(
                        red = 0.1f,
                        green = 0.8f - (barHeight / height),
                        blue = 0.2f,
                        alpha = 1.0f
                    ),
                    topLeft = Offset(
                        x = i * (barWidth + 2),
                        y = centerY - barHeight / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(
                        width = barWidth,
                        height = barHeight
                    )
                )
            }
        }
        else
        {
            // When inactive, show a simple sine wave animation
            val path = Path()
            path.moveTo(0f, centerY)

            val amplitude = height * 0.1f
            val frequency = 0.05f

            for (x in 0..width.toInt() step 2) {
                val y = centerY + amplitude * sin(x * frequency + phase)
                path.lineTo(x.toFloat(), y)
            }

            drawPath(
                path = path,
                color = Color.Gray,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}