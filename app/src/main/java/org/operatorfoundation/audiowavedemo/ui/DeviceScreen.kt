package org.operatorfoundation.audiowavedemo.ui

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun DeviceScreen(
    connectedDevices: List<UsbDevice>,
    onDeviceSelected: (UsbDevice) -> Unit,
    onRefreshRequested: () -> Unit,
    isConnected: Boolean,
    currentDeviceName: String,
    isProcessingActive: Boolean,
    onNavigateToProcessing: () -> Unit
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
                    text = "Found ${connectedDevices.size} USB audio device(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Device list
        Text(
            text = "Available Devices",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (connectedDevices.isEmpty())
        {
            // No devices found message
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No USB audio devices found.\nPlease connect a device and refresh.",
                    textAlign = TextAlign.Center
                )
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
                        isSelected = device == connectedDevices.find { it.deviceName == currentDeviceName },
                        onClick = { onDeviceSelected(device) }
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
                onClick = onRefreshRequested,
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

@Composable
fun DeviceItem(
    device: UsbDevice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Device: ${device.deviceName}",
                style = MaterialTheme.typography.bodyLarge
            )

            Text(
                text = "ID: ${device.deviceId}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}