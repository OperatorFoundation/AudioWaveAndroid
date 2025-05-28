package org.operatorfoundation.audiowavedemo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.operatorfoundation.audiowave.AudioCaptureCallback
import org.operatorfoundation.audiowave.AudioWaveManager
import org.operatorfoundation.audiowave.effects.Effect
import org.operatorfoundation.audiowave.utils.AudioUtils
import org.operatorfoundation.audiowavedemo.ui.DeviceScreen
import org.operatorfoundation.audiowavedemo.ui.ProcessingScreen
import org.operatorfoundation.audiowavedemo.ui.theme.AudioWaveDemoTheme
import timber.log.Timber

class MainActivity : ComponentActivity(), AudioCaptureCallback {
    private lateinit var audioWaveManager: AudioWaveManager
    private var connectedDevices by mutableStateOf<List<UsbDevice>>(emptyList())
    private var allUsbDevices by mutableStateOf<List<UsbDevice>>(emptyList())
    private var connectedDevice by mutableStateOf<UsbDevice?>(null)
    private var isProcessingActive by mutableStateOf(false)
    private var audioLevel by mutableStateOf(0f)
    private var availableDecoders by mutableStateOf<List<String>>(emptyList())
    private var activeDecoder by mutableStateOf<String?>(null)
    private var decodedData by mutableStateOf<ByteArray?>(null)
    private var activeEffects by mutableStateOf<List<Effect>>(emptyList())
    private var showAllUsbDevices by mutableStateOf(false)
    private var isDebugMode by mutableStateOf(false)
    private var isCollectingAudioData = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, initialize
            initializeAudioWave()
        } else {
            Toast.makeText(
                this,
                "Audio permission is required for this app to work",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        // TEMPORARY DEBUG LOG
        Timber.d("🟢 MainActivity.onCreate() started")

        // Test Timber logging
        Timber.d("MainActivity onCreate - Timber is working!")
        Timber.tag("TEST").d("Tagged timber log test")

        // Check if in debug mode
        isDebugMode = try
        {
            applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        }
        catch (e: Exception) { false }

        // Initialize AudioWave library
        audioWaveManager = AudioWaveManager.getInstance(applicationContext)
        audioWaveManager.setAudioCaptureCallback(this)

        // Check for required permissions
        if (!hasRequiredPermissions())
        {
            requestPermissions()
        }
        else
        {
            // Initialize AudioWave library
            initializeAudioWave()
        }

        setContent {
            AudioWaveDemoTheme {
                val navController = rememberNavController()

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = navController.currentBackStackEntry?.destination?.route == "devices",
                                onClick = { navController.navigate("devices") {
                                    popUpTo("devices") { inclusive = true }
                                }},
                                icon = { Icon(imageVector = Icons.Default.Home,
                                    contentDescription = "Devices") },
                                label = { Text("Devices") }
                            )

                            NavigationBarItem(
                                selected = navController.currentBackStackEntry?.destination?.route == "processing",
                                onClick = { navController.navigate("processing") {
                                    popUpTo("devices")
                                }},
                                icon = { Icon(imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Processing") },
                                label = { Text("Processing") }
                            )
                        }
                    }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "devices",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("devices") {
                            DeviceScreen(
                                connectedDevices = if (showAllUsbDevices) allUsbDevices else connectedDevices,
                                onDeviceSelected = { connectToDevice(it) },
                                onRefreshRequested = { initializeAudioWave() },
                                isConnected = connectedDevice != null,
                                currentDeviceName = connectedDevice?.deviceName ?: "",
                                isProcessingActive = isProcessingActive,
                                onNavigateToProcessing = { navController.navigate("processing") },
                                isDebugMode = isDebugMode,
                                showAllDevices = showAllUsbDevices,
                                onToggleShowAllDevices = { showAllDevices ->
                                    showAllUsbDevices = showAllDevices
                                    initializeAudioWave(showAllDevices)
                                }
                            )
                        }

                        composable("processing") {
                            ProcessingScreen(
                                isConnected = connectedDevice != null,
                                isProcessingActive = isProcessingActive,
                                onStartCapture = { startAudioCapture() },
                                onStopCapture = { stopAudioCapture() },
                                audioLevel = audioLevel,
                                availableDecoders = listOf("None (Raw Audio)") + availableDecoders,
                                activeDecoder = activeDecoder,
                                onDecoderSelected = { setDecoder(it) },
                                activeEffects = activeEffects,
                                onEffectAdded = { addEffect(it) },
                                onEffectRemoved = { removeEffect(it) },
                                onNavigateToDevices = { navController.navigate("devices") }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setupAudioDataCollection()
    {
        // Prevent multiple collection coroutines
        if (isCollectingAudioData) {
            Timber.d("🟨 Audio data collection already active, skipping setup")
            return
        }

        // TEMPORARY DEBUG LOG
        Timber.d("🟡 setupAudioDataCollection() CALLED")

        isCollectingAudioData = true

        lifecycleScope.launch {
            // TEMPORARY DEBUG LOG
            Timber.d("🟠 Inside lifecycleScope.launch")

            try
            {
                Timber.d("🎯 Setting up audio data collection...")

                // Collect from the audio flow
                audioWaveManager.captureFlow().collect { audioData ->
                    Timber.d("📊 Flow emitted data: ${audioData.size} bytes")

                    // Debug logging for received data
                    if (audioData.isNotEmpty()) {
                        Timber.d("✅ AUDIO DATA RECEIVED: ${audioData.size} bytes")

                        // Check if data is all zeros (silence)
                        val nonZeroBytes = audioData.count { it != 0.toByte() }
                        val hasRealData = nonZeroBytes > (audioData.size * 0.1) // At least 10% non-zero

                        Timber.d("Non-zero bytes: $nonZeroBytes/${audioData.size} (${nonZeroBytes * 100 / audioData.size}%), hasRealData: $hasRealData")

                        if (hasRealData) {
                            // Show first few bytes
                            val preview = audioData.take(16).joinToString(" ") { "%02x".format(it.toInt() and 0xFF) }
                            Timber.d("Data preview: $preview")

                            // Use AudioUtils to calculate RMS
                            val rmsLevel = AudioUtils.calculateRmsLevel(audioData)
                            Timber.d("AudioUtils RMS: $rmsLevel")
                        }
                    } else {
                        Timber.v("Empty audio data received")
                    }

                    // Calculate audio level and update UI
                    val level = AudioUtils.calculateDisplayLevel(audioData)
                    val hasData = AudioUtils.hasAudioSignal(audioData)

                    Timber.d("Audio analysis: level=$level, hasData=$hasData, size=${audioData.size}")

                    // Update the state on the main thread
                    withContext(Dispatchers.Main) {
                        audioLevel = level
                    }
                }
            }
            catch (e: Exception)
            {
                Timber.e(e, "Error in setupAudioDataCollection")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error collecting audio data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            finally
            {
                // Reset the flag when collection ends
                isCollectingAudioData = false
                Timber.d("🔴 Audio data collection ended")
            }
        }
    }

    fun initializeAudioWave(includeNonAudioDevices: Boolean = false) {
        lifecycleScope.launch {
            audioWaveManager.initialize().fold(
                onSuccess = {
                    // Update the available devices
                    connectedDevices = audioWaveManager.getConnectedDevices(includeNonAudioDevices)

                    // Get all USB devices for debug mode
                    if (isDebugMode) {
                        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
                        allUsbDevices = usbManager.deviceList.values.toList()
                    }

                    // Update available decoders
                    availableDecoders = audioWaveManager.getAvailableDecoders()

                    // Update active effects
                    activeEffects = audioWaveManager.getActiveEffects()

                    Timber.d("AudioWave initialized, found ${connectedDevices.size} audio devices and ${allUsbDevices.size} total USB devices")
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to initialize AudioWave library: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    Timber.e(error, "Failed to initialize AudioWave")
                }
            )
        }
    }

    private fun hasRequiredPermissions(): Boolean
    {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions()
    {
        // Use the ActivityResult API instead of the deprecated requestPermissions
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun connectToDevice(device: UsbDevice)
    {
        if (isDebugMode) { debugSelectedDevice(device) }

        lifecycleScope.launch {
            audioWaveManager.startCapture(device).fold(
                onSuccess = {
                    connectedDevice = device
                    isProcessingActive = true

                    // Set up data collection after successful connection
                    setupAudioDataCollection()

                    Toast.makeText(
                        this@MainActivity,
                        "Connected to device and started capture",
                        Toast.LENGTH_SHORT
                    ).show()

                    Timber.d("Connected to device: ${device.deviceName}")
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to connect to device: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    Timber.e(error, "Failed to connect to device")
                }
            )
        }
    }

    fun debugSelectedDevice(device: UsbDevice)
    {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        Timber.d("=== DEVICE DEBUG INFO ===")
        Timber.d("Device Name: ${device.deviceName}")
        Timber.d("Product Name: ${device.productName}")
        Timber.d("Manufacturer: ${device.manufacturerName}")
        Timber.d("Vendor ID: 0x${device.vendorId.toString(16)} (${device.vendorId})")
        Timber.d("Product ID: 0x${device.productId.toString(16)} (${device.productId})")
        Timber.d("Device Class: ${device.deviceClass}")
        Timber.d("Device Subclass: ${device.deviceSubclass}")
        Timber.d("Device Protocol: ${device.deviceProtocol}")
        Timber.d("Interface Count: ${device.interfaceCount}")

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            Timber.d("--- Interface $i ---")
            Timber.d("  Class: ${intf.interfaceClass} ${if (intf.interfaceClass == 1) "(AUDIO)" else ""}")
            Timber.d("  Subclass: ${intf.interfaceSubclass}")
            Timber.d("  Protocol: ${intf.interfaceProtocol}")
            Timber.d("  Endpoint Count: ${intf.endpointCount}")

            for (j in 0 until intf.endpointCount) {
                val endpoint = intf.getEndpoint(j)
                val direction = if (endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) "IN" else "OUT"
                val type = when (endpoint.type) {
                    android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "CONTROL"
                    android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISOCHRONOUS"
                    android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                    android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_INT -> "INTERRUPT"
                    else -> "UNKNOWN"
                }
                Timber.d("    Endpoint $j: $direction $type (maxPacket: ${endpoint.maxPacketSize})")
            }
        }
        Timber.d("=== END DEBUG INFO ===")
    }

    fun startAudioCapture() {
        val device = connectedDevice ?: return

        lifecycleScope.launch {
            audioWaveManager.startCapture(device).fold(
                onSuccess = {
                    isProcessingActive = true

                    // Set up data collection after starting capture
                    setupAudioDataCollection()

                    Toast.makeText(
                        this@MainActivity,
                        "Audio capture started",
                        Toast.LENGTH_SHORT
                    ).show()

                    Timber.d("Audio capture started")
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to start audio capture: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    Timber.e(error, "Failed to start audio capture")
                }
            )
        }
    }

    fun stopAudioCapture() {
        lifecycleScope.launch {
            audioWaveManager.stopCapture().fold(
                onSuccess = {
                    isProcessingActive = false

                    Toast.makeText(
                        this@MainActivity,
                        "Audio capture stopped",
                        Toast.LENGTH_SHORT
                    ).show()

                    Timber.d("Audio capture stopped")
                },
                onFailure = { error ->
                    Toast.makeText(
                        this@MainActivity,
                        "Failed to stop audio capture: ${error.message}",
                        Toast.LENGTH_SHORT
                    ).show()

                    Timber.e(error, "Failed to stop audio capture")
                }
            )
        }
    }

    fun setDecoder(decoderId: String) {
        if (decoderId == "None (Raw Audio)") {
            // No decoder selected
            activeDecoder = null
        } else {
            audioWaveManager.setDecoder(decoderId)
            activeDecoder = decoderId

            Timber.d("Set decoder to: $decoderId")
        }
    }

    fun addEffect(effect: Effect) {
        audioWaveManager.addEffect(effect)
        // Update the list of active effects
        activeEffects = audioWaveManager.getActiveEffects()

        Toast.makeText(
            this,
            "Added effect: ${effect.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun removeEffect(effectId: String) {
        audioWaveManager.removeEffect(effectId)
        // Update the list of active effects
        activeEffects = audioWaveManager.getActiveEffects()

        Toast.makeText(
            this,
            "Removed effect: $effectId",
            Toast.LENGTH_SHORT
        ).show()
    }

    // From AudioCaptureCallback interface
    override fun onAudioDataCaptured(data: ByteArray)
    {
        // Update audio level meter on UI thread
        val level = AudioUtils.calculateDisplayLevel(data)
        val hasAnyAudio = AudioUtils.hasAudioSignal(data)

        runOnUiThread {
            audioLevel = level

            // Log for debugging
            if (hasAnyAudio) {
                Timber.d("Audio data received: ${data.size} bytes, level: $level, hasAudio: $hasAnyAudio")
            }
        }
    }

    // From AudioCaptureCallback interface
    override fun onAudioDataDecoded(data: ByteArray) {
        // Process decoded data
        runOnUiThread {
            decodedData = data
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            audioWaveManager.release()
        }
    }
}