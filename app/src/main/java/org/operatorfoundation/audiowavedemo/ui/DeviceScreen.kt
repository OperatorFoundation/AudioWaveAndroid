package org.operatorfoundation.audiowavedemo.ui

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
)
{
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Status Section
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ){
            Column (
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ){
                Text(
                    text = if (isConnected)
                    {
                        "Connected to: $currentDeviceName" + if (isProcessingActive) " (Processing Active)" else ""
                    }
                    else
                    {
                        "No device connected"
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Found ${connectedDevices.size} ${if (showAllDevices) "USB" else "USB audio"} device(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Debug mode toggle
                if (isDebugMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show all USB devices:",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showAllDevices,
                            onCheckedChange = onToggleShowAllDevices
                        )
                    }

                    Text(
                        text = "Debug mode enabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Device list
        Text(
            text = if (showAllDevices) "All USB Devices" else "Available Audio Devices",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (connectedDevices.isEmpty())
        {
            // No devices found message
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (showAllDevices)
                            "No USB devices found."
                        else
                            "No USB audio devices found.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (showAllDevices)
                            "Please connect a USB device and refresh."
                        else
                            "To use this app, you need to connect a USB audio device.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!showAllDevices && isDebugMode) {
                        OutlinedButton(
                            onClick = { onToggleShowAllDevices(true) }
                        ) {
                            Text("Show All USB Devices")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Note: You may need a USB OTG adapter to connect USB devices to your Android device.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        else
        {
            // List of devices
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(connectedDevices) { device ->
                    DeviceItem(
                        device = device,
                        isSelected = device.deviceName == currentDeviceName,
                        onClick = { onDeviceSelected(device) },
                        // FIXED: Use proper audio device detection instead of device.deviceClass
                        isAudioDevice = !showAllDevices || isAudioDevice(device)
                    )
                }
            }
        }

        // Buttons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { onRefreshRequested() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Refresh Devices")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = onNavigateToProcessing,
                modifier = Modifier.weight(1f),
                enabled = isConnected
            ) {
                Text("Go to Processing")
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DeviceItem(
    device: UsbDevice,
    isSelected: Boolean,
    isAudioDevice: Boolean = true,
    onClick: () -> Unit
) {
    val expanded = remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else if (!isAudioDevice)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        onClick = {
            if (isAudioDevice) {
                onClick()
            } else {
                expanded.value = !expanded.value
            }
        }
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
                Text(
                    text = "Device: ${device.deviceName}",
                    style = MaterialTheme.typography.bodyLarge
                )

                if (!isAudioDevice) {
                    Surface(
                        onClick = { },
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "Non-Audio",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        onClick = { },
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "Audio Device",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = "ID: ${device.deviceId}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (device.productName != null) {
                Text(
                    text = "Product: ${device.productName}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (device.manufacturerName != null) {
                Text(
                    text = "Manufacturer: ${device.manufacturerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Show more details if expanded
            if (expanded.value || isAudioDevice) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "Device Class: ${device.deviceClass}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Device Subclass: ${device.deviceSubclass}",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Vendor ID: 0x${device.vendorId.toString(16)} (${device.vendorId})",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Product ID: 0x${device.productId.toString(16)} (${device.productId})",
                    style = MaterialTheme.typography.bodySmall
                )

                // Show interface information for audio devices
                if (isAudioDevice) {
                    Text(
                        text = "Interfaces: ${device.interfaceCount}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    for (i in 0 until device.interfaceCount) {
                        val intf = device.getInterface(i)
                        if (intf.interfaceClass == 1) {
                            Text(
                                text = "  Audio Interface $i: class=${intf.interfaceClass}, subclass=${intf.interfaceSubclass}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (!isAudioDevice) {
                    Text(
                        text = "This device is not recognized as a USB audio device and cannot be used with AudioWave.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}