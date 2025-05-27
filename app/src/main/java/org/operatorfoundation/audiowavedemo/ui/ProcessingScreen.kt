package org.operatorfoundation.audiowavedemo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.operatorfoundation.audiowave.effects.Effect
import org.operatorfoundation.audiowave.effects.GainEffect
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    isConnected: Boolean,
    isProcessingActive: Boolean,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    audioLevel: Float,
    availableDecoders: List<String>,
    activeDecoder: String?,
    activeEffects: List<Effect>,
    onEffectAdded: (Effect) -> Unit,
    onEffectRemoved: (String) -> Unit,
    onDecoderSelected: (String) -> Unit,
    onNavigateToDevices: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Enhanced Status Section with Connection Details
        StatusCard(
            isConnected = isConnected,
            isProcessingActive = isProcessingActive,
            audioLevel = audioLevel
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Enhanced Audio Visualizer
        AudioVisualizerCard(
            audioLevel = audioLevel,
            isProcessingActive = isProcessingActive
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Control Buttons Section
        ControlButtonsCard(
            isConnected = isConnected,
            isProcessingActive = isProcessingActive,
            onStartCapture = onStartCapture,
            onStopCapture = onStopCapture
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Audio Level Meter
        AudioLevelCard(audioLevel = audioLevel)

        Spacer(modifier = Modifier.height(16.dp))

        // Effects Section (Collapsed by default to save space)
        EffectsCard(
            activeEffects = activeEffects,
            onEffectAdded = onEffectAdded,
            onEffectRemoved = onEffectRemoved
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Decoder Selection
        DecoderCard(
            availableDecoders = availableDecoders,
            activeDecoder = activeDecoder,
            onDecoderSelected = onDecoderSelected
        )

        Spacer(modifier = Modifier.weight(1f))

        // Navigation
        OutlinedButton(
            onClick = onNavigateToDevices,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Devices")
        }
    }
}

@Composable
private fun StatusCard(
    isConnected: Boolean,
    isProcessingActive: Boolean,
    audioLevel: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isProcessingActive && audioLevel > 0.01f -> MaterialTheme.colorScheme.primaryContainer
                isProcessingActive -> MaterialTheme.colorScheme.secondaryContainer
                isConnected -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = when {
                            isProcessingActive && audioLevel > 0.01f -> "🎵 Audio Streaming"
                            isProcessingActive -> "⏸️ Processing (No Audio)"
                            isConnected -> "🔗 Device Connected"
                            else -> "❌ No Device"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = when {
                            isProcessingActive && audioLevel > 0.01f -> "Receiving audio data from device"
                            isProcessingActive -> "Connected but no audio detected"
                            isConnected -> "Ready to start audio capture"
                            else -> "Connect a USB audio device first"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Icon(
                    imageVector = if (audioLevel > 0.01f) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = if (audioLevel > 0.01f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isProcessingActive) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = audioLevel,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (audioLevel > 0.01f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )

                Text(
                    text = "Audio Level: ${(audioLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AudioVisualizerCard(
    audioLevel: Float,
    isProcessingActive: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessingActive) {
                RealTimeWaveform(
                    audioLevel = audioLevel,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Start capture to see audio visualization",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RealTimeWaveform(
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    var time by remember { mutableStateOf(0f) }

    LaunchedEffect(audioLevel) {
        while (true) {
            time += 0.1f
            kotlinx.coroutines.delay(50) // 20 FPS
        }
    }

    Canvas(modifier = modifier.padding(16.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        if (audioLevel > 0.001f) {
            // Draw animated waveform based on audio level
            val amplitude = audioLevel * centerY * 0.8f
            val frequency = 2f + audioLevel * 3f

            for (x in 0 until width.toInt() step 4) {
                val normalizedX = x / width
                val waveY = sin((normalizedX * frequency + time) * 2 * Math.PI).toFloat()
                val y = centerY + waveY * amplitude

                // Add some randomness for more realistic look
                val noise = (kotlin.random.Random.nextFloat() - 0.5f) * audioLevel * 20f
                val finalY = y + noise

                drawCircle(
                    color = Color.Blue.copy(alpha = 0.6f + audioLevel * 0.4f),
                    radius = 2f + audioLevel * 4f,
                    center = Offset(x.toFloat(), finalY)
                )
            }
        } else {
            // Draw flat line when no audio
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
private fun ControlButtonsCard(
    isConnected: Boolean,
    isProcessingActive: Boolean,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Audio Capture Control",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onStartCapture,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected && !isProcessingActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Capture")
                }

                Button(
                    onClick = onStopCapture,
                    modifier = Modifier.weight(1f),
                    enabled = isProcessingActive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Capture")
                }
            }

            if (!isConnected) {
                Text(
                    text = "⚠️ Connect a USB audio device first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun AudioLevelCard(audioLevel: Float) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Level",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${(audioLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        audioLevel > 0.7f -> Color.Red
                        audioLevel > 0.3f -> Color.Yellow
                        audioLevel > 0.05f -> Color.Green
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom audio level bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(audioLevel)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                audioLevel > 0.7f -> Color.Red
                                audioLevel > 0.3f -> Color.Yellow
                                audioLevel > 0.05f -> Color.Green
                                else -> Color.Gray
                            }
                        )
                )
            }

            // Status text
            Text(
                text = when {
                    audioLevel > 0.05f -> "✅ Audio signal detected"
                    audioLevel > 0.001f -> "🔉 Weak signal"
                    else -> "🔇 No audio signal"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun EffectsCard(
    activeEffects: List<Effect>,
    onEffectAdded: (Effect) -> Unit,
    onEffectRemoved: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Effects (${activeEffects.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(
                    onClick = { expanded = !expanded }
                ) {
                    Text(if (expanded) "Collapse" else "Expand")
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onEffectAdded(GainEffect()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Gain")
                    }
                }

                if (activeEffects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Active Effects:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    activeEffects.forEach { effect ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = effect.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(
                                onClick = { onEffectRemoved(effect.id) }
                            ) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecoderCard(
    availableDecoders: List<String>,
    activeDecoder: String?,
    onDecoderSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = activeDecoder ?: "None (Raw Audio)"

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Audio Decoder",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedText,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    label = { Text("Selected Decoder") }
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    availableDecoders.forEach { decoder ->
                        DropdownMenuItem(
                            text = { Text(decoder) },
                            onClick = {
                                onDecoderSelected(decoder)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}