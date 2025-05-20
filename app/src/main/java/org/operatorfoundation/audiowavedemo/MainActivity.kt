package org.operatorfoundation.audiowavedemo

import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.Manifest
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import org.operatorfoundation.audiowave.AudioCaptureCallback
import org.operatorfoundation.audiowave.AudioWaveManager
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : ComponentActivity(), AudioCaptureCallback {
    private lateinit var navController: NavController
    private lateinit var audioWaveManager: AudioWaveManager
    private lateinit var viewModel: AudioViewModel

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[AudioViewModel::class.java]

        // Initialize AudioWave library
        audioWaveManager = AudioWaveManager.getInstance(applicationContext)
        audioWaveManager.setAudioCaptureCallback(this)

        // Set up navigation
        val navView: BottomNavigationView = findViewById(R.id.nav_view)
        navController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_device, R.id.navigation_processing)
        )
        // ComponentActivity doesn't have ActionBar by default, so we skip setupActionBarWithNavController
        navView.setupWithNavController(navController)

        // Check for required permissions
        if (!hasRequiredPermissions()) {
            requestPermissions()
        }

        // Initialize AudioWave library
        initializeAudioWave()
    }

    private fun initializeAudioWave() {
        audioWaveManager.initialize { success ->
            runOnUiThread {
                if (success) {
                    // Update the available devices
                    viewModel.updateConnectedDevices(audioWaveManager.getConnectedDevices())

                    // Update available decoders
                    viewModel.updateAvailableDecoders(audioWaveManager.getAvailableDecoders())
                } else {
                    Toast.makeText(
                        this,
                        "Failed to initialize AudioWave library",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        // Use the ActivityResult API instead of the deprecated requestPermissions
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun connectToDevice(device: UsbDevice) {
        audioWaveManager.openDevice(device) { success ->
            runOnUiThread {
                if (success) {
                    viewModel.setConnectedDevice(device)
                    Toast.makeText(this, "Connected to device", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to connect to device", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun startAudioCapture() {
        val device = viewModel.connectedDevice.value ?: return

        audioWaveManager.startCapture(device) { success ->
            runOnUiThread {
                if (success) {
                    viewModel.setProcessingActive(true)
                    Toast.makeText(this, "Audio capture started", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to start audio capture", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun stopAudioCapture() {
        audioWaveManager.stopCapture { success ->
            runOnUiThread {
                if (success) {
                    viewModel.setProcessingActive(false)
                    Toast.makeText(this, "Audio capture stopped", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to stop audio capture", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun setDecoder(decoderId: String) {
        audioWaveManager.setDecoder(decoderId)
        viewModel.setActiveDecoder(decoderId)
    }

    override fun onAudioDataCaptured(data: ByteArray) {
        // Update audio level meter on UI thread
        val level = calculateAudioLevel(data)
        runOnUiThread {
            viewModel.setAudioLevel(level)
        }
    }

    override fun onAudioDataDecoded(data: ByteArray) {
        // Process decoded data
        runOnUiThread {
            viewModel.setDecodedData(data)
        }
    }

    private fun calculateAudioLevel(data: ByteArray): Float {
        // Calculate RMS audio level
        var sum = 0.0
        var count = 0

        for (i in 0 until data.size - 1 step 2) {
            if (i + 1 < data.size) {
                // Convert two bytes to a 16-bit sample
                val sample = (data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)
                sum += sample * sample
                count++
            }
        }

        if (count == 0) return 0f

        val rms = Math.sqrt(sum / count)
        // Normalize to 0.0 - 1.0 range (16-bit audio has range -32768 to 32767)
        return (rms / 32768.0).toFloat()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioWaveManager.release()
    }
}
