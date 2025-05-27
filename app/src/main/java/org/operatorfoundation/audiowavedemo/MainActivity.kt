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

        // Set up audio data collection
        setupAudioDataCollection()

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
        lifecycleScope.launch {
            try {
                // Collect from the audio flow
                audioWaveManager.captureFlow().collect { audioData ->
                    Timber.d("Raw audio data received: ${audioData.size} bytes")

                    // Log first few bytes to see if we're getting data
                    if (audioData.size >= 8) {
                        val firstBytes = audioData.take(8).joinToString(" ") { "%02x".format(it) }
                        Timber.d("First 8 bytes: $firstBytes")
                    }

                    // Update audio level meter
                    val level = calculateAudioLevel(audioData)
                    val hasData = isAnyAudioDataReceived(audioData)

                    Timber.d("Audio analysis: level=$level, hasData=$hasData")
                    // Update the state on the main thread
                    withContext(Dispatchers.Main) {
                        audioLevel = level
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error collecting audio data")
                // Show a toast with the error
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Error collecting audio data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
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
        val level = calculateAudioLevel(data)
        val hasAnyAudio = isAnyAudioDataReceived(data)

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

    private fun calculateAudioLevel(data: ByteArray): Float
    {
        if (data.isEmpty()) return 0f

        // Calculate RMS audio level
        var sum = 0.0
        var count = 0
        var maxSample = 0.0
        var minSample = 0.0

        // Convert bytes to 16-bit samples and analyze
        for (i in 0 until data.size - 1 step 2)
        {
            if (i + 1 < data.size)
            {
                // Convert two bytes to a 16-bit signed sample
                val sample = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i].toInt() and 0xFF)
                val signedSample = if (sample > 32767) sample - 65536 else sample
                val normalizedSample = signedSample.toDouble()

                sum += normalizedSample * normalizedSample
                maxSample = maxOf(maxSample, kotlin.math.abs(normalizedSample))
                minSample = minOf(minSample, kotlin.math.abs(normalizedSample))
                count++
            }
        }

        if (count == 0) return 0f

        // Calculate multiple metrics for better weak signal detection
        val rms = Math.sqrt(sum / count)
        val peak = maxSample
        val dynamicRange = maxSample - minSample

        // Normalize RMS to 0.0 - 1.0 range (16-bit audio has range -32768 to 32767)
        val normalizedRMS = (rms / 32768.0).toFloat()

        // For WSPR and other weak signals, use a more sensitive calculation
        val sensitiveLevel = when {
            normalizedRMS > 0.1f -> normalizedRMS // Normal audio levels
            normalizedRMS > 0.001f -> normalizedRMS * 10f // Boost weak signals for display
            dynamicRange > 100 -> (dynamicRange / 32768.0 * 5).toFloat() // Detect any signal variation
            else -> 0f
        }

        // Log details for debugging
        if (sensitiveLevel > 0.001f) {
            Timber.d("Audio detected - RMS: $normalizedRMS, Peak: ${peak/32768}, Range: $dynamicRange, Final: $sensitiveLevel")
        }

        return sensitiveLevel.coerceIn(0f, 1f)
    }

    // Detect if ANY audio is being received even if it is very quiet
    private fun isAnyAudioDataReceived(data: ByteArray): Boolean
    {
        if (data.isEmpty()) return false

        // Check that data is not just zeros/silence
        var nonZeroCount = 0
        var totalVariation = 0.0
        var previousSample = 0

        for (i in 0 until data.size - 1 step 2)
        {
            if (i + 1 < data.size)
            {
                val sample = ((data[i + 1].toInt() and 0xFF) shl 8) or (data[i].toInt() and 0xFF)

                if (sample != 0) nonZeroCount++

                // Check for variation between samples (indicate actual audio data)
                totalVariation += kotlin.math.abs(sample - previousSample)
                previousSample = sample
            }
        }

        val hasNonZeroData = nonZeroCount > (data.size / 4) // At least 25% non-zero
        val hasVariation = totalVariation > (data.size * 0.1) // Some variation in the signal

        Timber.d("Audio data analysis - NonZero: $nonZeroCount/${data.size/2}, Variation: $totalVariation, HasAudio: ${hasNonZeroData || hasVariation}")

        return hasNonZeroData || hasVariation
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            audioWaveManager.release()
        }
    }
}