package org.operatorfoundation.audiowavedemo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.operatorfoundation.audiowave.Effect
import org.operatorfoundation.audiowave.effects.GainEffect
import org.operatorfoundation.audiowave.effects.EchoEffect
import org.operatorfoundation.audiowavedemo.ui.components.AudioLevelIndicator
import org.operatorfoundation.audiowavedemo.ui.components.AudioVisualizer

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
        // Status section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = if (isConnected) {
                        if (isProcessingActive) "Audio Capture Active" else "Device Connected"
                    } else {
                        "No Device Connected"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Audio visualizer area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Audio Visualizer Component
                AudioVisualizer(
                    audioLevel = audioLevel,
                    isActive = isProcessingActive
                )
            }
        }

        // Audio level indicator
        Text(
            text = "Audio Level",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        AudioLevelIndicator(
            level = audioLevel,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(bottom = 16.dp)
        )

        // Effects section
        Text(
            text = "Effects",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onEffectAdded(GainEffect()) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Add Gain")
            }

//            Button(
//                onClick = { onEffectAdded(NoiseGateEffect(threshold = 0.1f)) },
//                modifier = Modifier.weight(1f)
//            ) {
//                Text("Add Noise Gate")
//            }
        }

        // Active effects list
        if (activeEffects.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Active Effects",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    activeEffects.forEach { effect ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(effect.name)
                            Button(
                                onClick = { onEffectRemoved(effect.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Remove")
                            }
                        }
                        Divider()
                    }
                }
            }
        }

        // Decoder selection
        Text(
            text = "Decoder",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        var expanded by remember { mutableStateOf(false) }
        val selectedText = activeDecoder ?: "None (Raw Audio)"

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            TextField(
                value = selectedText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
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

        // Control buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onStartCapture,
                modifier = Modifier.weight(1f),
                enabled = isConnected && !isProcessingActive
            ) {
                Text("Start Capture")
            }

            Button(
                onClick = onStopCapture,
                modifier = Modifier.weight(1f),
                enabled = isProcessingActive
            ) {
                Text("Stop Capture")
            }
        }

        // Navigation button
        Button(
            onClick = onNavigateToDevices,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Devices")
        }
    }
}