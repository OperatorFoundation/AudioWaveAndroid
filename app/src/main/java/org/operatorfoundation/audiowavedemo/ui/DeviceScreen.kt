package org.operatorfoundation.audiowavedemo.ui

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Helper function to properly check if a device is an audio device
private fun isAudioDevice(device: UsbDevice): Boolean {
    for (i in 0 until device.interfaceCount) {
        val intf = device.getInterface(i)
        if (intf.interfaceClass == 1) { // USB Audio Class
            return true
        }
    }
    return false
}

@Composable
fun DeviceScreen(
    connectedDevices: List<UsbDevice>,
    onDeviceSelected: (UsbDevice) -> Unit,
    onRefreshRequested: () -> Unit,
    isConnected: Boolean,
    currentDeviceName: String,
    isProcessingActive: Boolean,
    onNavigateToProcessing: () -> Unit,
    isDebugMode: Boolean = false,
    showAllDevices: Boolean = false,
    onToggleShowAllDevices: (Boolean) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Enhanced Status Section
        EnhancedStatusCard(
            isConnected = isConnected,
            currentDeviceName = currentDeviceName,
            isProcessingActive = isProcessingActive,
            deviceCount = connectedDevices.size,
            showAllDevices = showAllDevices,
            isDebugMode = isDebugMode,
            onToggleShowAllDevices = onToggleShowAllDevices
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Device List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showAllDevices) "All USB Devices" else "Audio Devices",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (connectedDevices.isNotEmpty()) {
                Badge {
                    Text("${connectedDevices.size}")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (connectedDevices.isEmpty()) {
            // Enhanced Empty State
            EmptyDeviceState(
                showAllDevices = showAllDevices,
                isDebugMode = isDebugMode,
                onToggleShowAllDevices = onToggleShowAllDevices
            )
        } else {
            // Device List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(connectedDevices) { device ->
                    EnhancedDeviceItem(
                        device = device,
                        isSelected = device.deviceName == currentDeviceName,
                        onClick = { onDeviceSelected(device) },
                        isAudioDevice = !showAllDevices || isAudioDevice(device),
                        showDebugInfo = showAllDevices
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        ActionButtonsRow(
            onRefreshRequested = onRefreshRequested,
            onNavigateToProcessing = onNavigateToProcessing,
            isConnected = isConnected,
            hasProcessingToShow = isConnected
        )
    }
}

@Composable
private fun EnhancedStatusCard(
    isConnected: Boolean,
    currentDeviceName: String,
    isProcessingActive: Boolean,
    deviceCount: Int,
    showAllDevices: Boolean,
    isDebugMode: Boolean,
    onToggleShowAllDevices: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isProcessingActive -> MaterialTheme.colorScheme.primaryContainer
                isConnected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isProcessingActive -> "🎵 Audio Streaming Active"
                            isConnected -> "🔗 Device Connected"
                            else -> "📱 Ready to Connect"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (isConnected) {
                        Text(
                            text = currentDeviceName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Icon(
                    imageVector = when {
                        isProcessingActive -> Icons.Default.VolumeUp
                        isConnected -> Icons.Default.AudioFile
                        else -> Icons.Default.Usb
                    },
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Found $deviceCount ${if (showAllDevices) "USB" else "audio"} device(s)",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (isDebugMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show all:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = showAllDevices,
                            onCheckedChange = onToggleShowAllDevices,
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
            }

            if (isDebugMode) {
                Text(
                    text = "🔧 Debug mode enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyDeviceState(
    showAllDevices: Boolean,
    isDebugMode: Boolean,
    onToggleShowAllDevices: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Usb,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (showAllDevices) "No USB devices found" else "No USB audio devices found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (showAllDevices) {
                    "Connect a USB device via OTG adapter and refresh"
                } else {
                    "Connect a USB audio device (like the Teensy) and refresh"
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (!showAllDevices && isDebugMode) {
                OutlinedButton(
                    onClick = { onToggleShowAllDevices(true) }
                ) {
                    Text("Show All USB Devices")
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "💡 Tip: Make sure your USB OTG adapter is working and the device is properly connected",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun EnhancedDeviceItem(
    device: UsbDevice,
    isSelected: Boolean,
    isAudioDevice: Boolean,
    showDebugInfo: Boolean,
    onClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                !isAudioDevice -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        onClick = {
            if (isAudioDevice) {
                onClick()
            } else {
                expanded = !expanded
            }
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.productName ?: "Unknown Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = device.manufacturerName ?: "Unknown Manufacturer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = device.deviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Badge(
                        containerColor = if (isAudioDevice) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Text(
                            text = if (isAudioDevice) "AUDIO" else "NON-AUDIO",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    if (isSelected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "CONNECTED",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // Show device details if expanded or if it's an audio device
            if (expanded || (isAudioDevice && showDebugInfo)) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Vendor ID: 0x${device.vendorId.toString(16)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Product ID: 0x${device.productId.toString(16)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Device ID: ${device.deviceId}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Interfaces: ${device.interfaceCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Show audio interfaces for audio devices
                if (isAudioDevice) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Audio Interfaces:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    for (i in 0 until device.interfaceCount) {
                        val intf = device.getInterface(i)
                        if (intf.interfaceClass == 1) {
                            Text(
                                text = "  Interface $i: class=${intf.interfaceClass}, subclass=${intf.interfaceSubclass}, endpoints=${intf.endpointCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (!isAudioDevice) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ This device cannot be used for audio capture",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtonsRow(
    onRefreshRequested: () -> Unit,
    onNavigateToProcessing: () -> Unit,
    isConnected: Boolean,
    hasProcessingToShow: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(
            onClick = onRefreshRequested,
            modifier = Modifier.weight(1f)
        ) {
            Text("🔄 Refresh Devices")
        }

        Button(
            onClick = onNavigateToProcessing,
            modifier = Modifier.weight(1f),
            enabled = hasProcessingToShow
        ) {
            Text(
                if (isConnected) "🎵 Go to Processing" else "Processing"
            )
        }
    }
}