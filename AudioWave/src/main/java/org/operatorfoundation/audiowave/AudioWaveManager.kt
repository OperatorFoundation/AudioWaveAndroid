package org.operatorfoundation.audiowave

import android.content.Context
import android.hardware.usb.UsbDevice
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * AudioWave - A library for processing USB audio input with support for
 * various audio processing and decoding capabilities.
 *
 * This is the main entry point for the AudioWave library. It provides a simple API
 * for discovering and connecting to USB audio devices, capturing audio data,
 * processing it with effects, and decoding it with various decoders.
 *
 * Usage example with coroutines:
 * ```
 * // Initialize the library
 * val audioWave = AudioWaveManager.getInstance(context)
 * lifecycleScope.launch {
 *     val success = audioWave.initialize()
 *     if (success) {
 *         // Get connected devices
 *         val devices = audioWave.getConnectedDevices()
 *
 *         // Connect to the first device
 *         if (devices.isNotEmpty()) {
 *             val captureStarted = audioWave.startCapture(devices[0])
 *             if (captureStarted) {
 *                 // Started capturing audio
 *             }
 *         }
 *     }
 * }
 *
 * // Set a callback to receive audio data
 * audioWave.setAudioCaptureCallback(object : AudioCaptureCallback {
 *     override fun onAudioDataCaptured(data: ByteArray) {
 *         // Raw audio data from the USB device
 *     }
 *
 *     override fun onAudioDataDecoded(data: ByteArray) {
 *         // Decoded data (if a decoder is active)
 *     }
 * })
 * ```
 */

class AudioWaveManager private constructor(private val context: Context)
{
    companion object
    {
        private const val TAG = "AudioWaveManager"

        // Use weakReference to prevent memory leaks
        private var weakInstance: WeakReference<AudioWaveManager>? = null

        /**
         * Get the singleton instance of AudioWaveManager.
         *
         * Note: This implementation uses the application context to prevent memory leaks.
         * Always prefer using the application context rather than an activity context.
         *
         * @param context Application context (will be converted to application context if it's not already)
         * @return The AudioWaveManager instance
         */
        @Synchronized
        fun getInstance(context: Context): AudioWaveManager
        {
            val appContext = context.applicationContext

            // Check if we have a valid instance that hasn't been garbage collected
            val existingInstance = weakInstance?.get()

            if (existingInstance != null)
            {
                return existingInstance
            }

            // Create a new instance and store a weak reference
            val newInstance = AudioWaveManager(appContext)
            weakInstance = WeakReference(newInstance)
            return newInstance
        }
    }

    private val executorService = Executors.newSingleThreadExecutor()
    private val usbDeviceManager = UsbDeviceManager(context)
}