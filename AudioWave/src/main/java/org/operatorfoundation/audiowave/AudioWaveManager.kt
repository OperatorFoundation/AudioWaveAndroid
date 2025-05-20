package org.operatorfoundation.audiowave

import android.content.Context
import android.hardware.usb.UsbDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.operatorfoundation.audiowave.exception.AudioException
import org.operatorfoundation.audiowave.threading.AudioThreadManager
import org.operatorfoundation.audiowave.usb.UsbDeviceDiscovery
import org.operatorfoundation.audiowave.usb.UsbPermissionManager
import org.operatorfoundation.audiowave.utils.ErrorHandler
import timber.log.Timber
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * AudioWave - A library for processing USB audio input with support for
 * various audio processing and decoding capabilities.
 *
 * This is the main entry point for the AudioWave library. It provides an API
 * for discovering and connecting USB audio devices, capturing audio data,
 * processing it with effects, and decoding it with various decoders.
 *
 * Usage example with coroutines and modern Kotlin features:
 * ```
 * // Initialize the library
 * val audioWave = AudioWaveManager.getInstance(context)
 * lifecycleScope.launch {
 *     audioWave.initialize().fold(
 *         onSuccess = {
 *             // Get connected devices
 *             val devices = audioWave.getConnectedDevices()
 *
 *             // Connect to the first device
 *             if (devices.isNotEmpty()) {
 *                 audioWave.startCapture(devices[0]).fold(
 *                     onSuccess = {
 *                         // Started capturing audio
 *                     },
 *                     onFailure = { error ->
 *                         // Handle capture start error
 *                         showErrorMessage(ErrorHandler.getErrorMessage(error))
 *                     }
 *                 )
 *             }
 *         },
 *         onFailure = { error ->
 *             // Handle initialization error
 *             showErrorMessage(ErrorHandler.getErrorMessage(error))
 *         }
 *     )
 * }
 *
 * // Set a callback to receive audio data
 * audioWave.captureFlow().collect { audioData ->
 *     // Process the audio data
 *     processAudioData(audioData)
 * }
 * ```
 */

class AudioWaveManager private constructor(private val context: Context)
{
    companion object {
        private const val TAG = "AudioWaveManager"

        // Using weakReference to prevent memory leaks
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
        fun getInstance(context: Context): AudioWaveManager {
            // Use application context to prevent memory leaks
            val appContext = context.applicationContext

            // Check if we have a valid instance that hasn't been garbage collected
            val existingInstance = weakInstance?.get()
            if (existingInstance != null) {
                return existingInstance
            }

            // Create new instance and store weak reference
            val newInstance = AudioWaveManager(appContext)
            weakInstance = WeakReference(newInstance)
            return newInstance
        }
    }

    private val threadManager = AudioThreadManager(2)
    private val usbDeviceDiscovery = UsbDeviceDiscovery(context)
    private val usbPermissionManager = UsbPermissionManager(context)
    private val audioProcessor = AudioProcessor()
    private val decoderRegistry = DecoderRegistry()

    private var audioCaptureCallback: AudioCaptureCallback? = null
    private var activeDecoder: AudioDecoder? = null
    private var isCapturing = false

    /**
     * Initialize the AudioWave library and set up USB device detection.
     * This must be called before using any other methods.
     *
     * @return Result indicating success or failure with error details
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext ErrorHandler.runCatching {
            Timber.d("Initializing AudioWave")
            Unit
        }
    }

    /**
     * Register a callback to receive raw audio data and decoded data.
     * The callback will be invoked on a background thread, so UI operations
     * should be posted to the main thread.
     *
     * Note: For reactive programming, consider using [captureFlow] instead.
     *
     * @param callback The callback to receive audio data
     */
    fun setAudioCaptureCallback(callback: AudioCaptureCallback) {
        this.audioCaptureCallback = callback
    }

    /**
     * Provides a Flow of captured audio data for reactive processing.
     * This is the preferred way to receive audio data in a reactive application.
     *
     * @return Flow emitting captured audio data
     */
    fun captureFlow(): Flow<ByteArray> = flow {
        // Implementation would emit audio data as it's captured
        // This is a placeholder that would be implemented based on actual capture mechanisms
        Timber.d("captureFlow() called but not fully implemented")
    }.flowOn(Dispatchers.IO)

    /**
     * Set an audio decoder to process the captured audio data.
     *
     * @param decoderId The ID of the decoder to use
     * @see getAvailableDecoders
     */
    fun setDecoder(decoderId: String) {
        activeDecoder = decoderRegistry.getDecoder(decoderId)
        Timber.d("Set active decoder to: $decoderId")
    }

    /**
     * Get a list of available audio decoders IDs.
     *
     * @return A list of decoder IDs
     */
    fun getAvailableDecoders(): List<String> {
        return decoderRegistry.getAvailableDecoders()
    }

    /**
     * Get detailed information about a specific decoder.
     *
     * @param decoderId The ID of the decoder
     * @return A map containing the decoder's information, or null if not found
     */
    fun getDecoderInfo(decoderId: String): Map<String, String>? {
        return decoderRegistry.getDecoderInfo(decoderId)
    }

    /**
     * Get a list of connected USB audio devices.
     *
     * @return A list of connected USB audio devices
     */
    fun getConnectedDevices(): List<UsbDevice> {
        return usbDeviceDiscovery.findAudioDevices()
    }

    /**
     * Start capturing audio from the specified USB device.
     *
     * @param device The USB device to capture audio from
     * @return Result indicating success or failure with error details
     */
    suspend fun startCapture(device: UsbDevice): Result<Unit> = withContext(Dispatchers.IO) {
        if (isCapturing) {
            Timber.w("Capture already in progress")
            return@withContext Result.failure(AudioException.AudioProcessingException("Capture already in progress"))
        }

        return@withContext ErrorHandler.runCatching {
            // Request permission if needed
            val hasPermission = usbPermissionManager.requestPermission(device).getOrThrow()
            if (!hasPermission) {
                throw AudioException.PermissionDeniedException("Permission denied for device: ${device.deviceName}")
            }

            // Start audio capture on a background thread
            threadManager.executeTask {
                isCapturing = true

                // TODO: Implement actual audio stream capture from device

                Timber.d("Audio capture started from device: ${device.deviceName}")
            }
        }
    }

    /**
     * Stop capturing audio.
     *
     * @return Result indicating success or failure with error details
     */
    suspend fun stopCapture(): Result<Unit> = withContext(Dispatchers.IO) {
        if (!isCapturing) {
            Timber.w("No capture in progress")
            return@withContext Result.failure(AudioException.AudioProcessingException("No capture in progress"))
        }

        return@withContext ErrorHandler.runCatching {
            // TODO: Implement actual audio stream stopping

            isCapturing = false
            Timber.d("Audio capture stopped")
        }
    }

    /**
     * Check if audio is currently being captured.
     *
     * @return True if capturing, false otherwise
     */
    fun isCapturing(): Boolean {
        return isCapturing
    }

    /**
     * Add an effect to the audio processing chain.
     * Effects are applied in the order they are added.
     *
     * @param effect The effect to add
     * @return This AudioWaveManager instance for method chaining
     */
    fun addEffect(effect: Effect): AudioWaveManager {
        audioProcessor.add(effect)
        return this
    }

    /**
     * Remove an effect from the audio processing chain.
     *
     * @param effectId The ID of the effect to remove
     * @return This AudioWaveManager instance for method chaining
     */
    fun removeEffect(effectId: String): AudioWaveManager {
        audioProcessor.remove(effectId)
        return this
    }

    /**
     * Get a list of currently active effects.
     *
     * @return A list of active effects
     */
    fun getActiveEffects(): List<Effect> {
        return audioProcessor.getEffects()
    }

    /**
     * Release resources when no longer needed.
     * The manager will not be usable after this call.
     *
     * @return Result indicating success or failure with error details
     */
    suspend fun release(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext ErrorHandler.runCatching {
            if (isCapturing) {
                // Stop capture if active
                stopCapture().getOrThrow()
            }

            threadManager.shutdown()
            usbPermissionManager.release()

            // Remove the instance from the weak reference when released
            synchronized(AudioWaveManager::class.java) {
                if (weakInstance?.get() === this@AudioWaveManager) {
                    weakInstance = null
                }
            }

            Timber.d("AudioWave resources released")
        }
    }
}