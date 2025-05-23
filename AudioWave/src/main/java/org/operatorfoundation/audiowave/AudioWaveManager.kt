package org.operatorfoundation.audiowave

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.operatorfoundation.audiowave.codec.AudioDecoder
import org.operatorfoundation.audiowave.decoder.DecoderRegistry
import org.operatorfoundation.audiowave.effects.Effect
import org.operatorfoundation.audiowave.exception.AudioException
import org.operatorfoundation.audiowave.threading.AudioThreadManager
import org.operatorfoundation.audiowave.usb.UsbAudioCapture
import org.operatorfoundation.audiowave.usb.UsbDeviceDiscovery
import org.operatorfoundation.audiowave.usb.UsbPermissionManager
import org.operatorfoundation.audiowave.utils.ErrorHandler
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * AudioWave - A library for processing USB audio input with support for
 * various audio processing and decoding capabilities.
 *
 * This is the main entry point for the AudioWave library. It provides a simple API
 * for discovering and connecting to USB audio devices, capturing audio data,
 * processing it with effects, and decoding it with various decoders.
 */
class AudioWaveManager private constructor(private val context: Context)
{
    companion object
    {
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

    private var audioCapture: UsbAudioCapture? = null
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
        if (!isCapturing) {
            Timber.w("Attempting to collect from captureFlow when not capturing")
            return@flow
        }

        val capture = audioCapture
        if (capture == null) {
            Timber.e("Audio capture is null despite isCapturing=true")
            return@flow
        }

        try {
            while (isCapturing && capture.isCapturing()) {
                // Read audio data from the USB device
                val audioDataResult = capture.readAudioData()

                if (audioDataResult.isSuccess) {
                    val audioData = audioDataResult.getOrThrow()

                    // Process the audio data through the processing chain
                    val processedData = processAudioData(audioData)

                    // Notify callback if registered
                    audioCaptureCallback?.onAudioDataCaptured(processedData)

                    // Emit the processed data to the flow
                    emit(processedData)
                } else {
                    // Handle error case
                    val error = audioDataResult.exceptionOrNull()
                    Timber.e(error, "Error reading audio data")
                    delay(100) // Wait a short time before retrying
                    // No continue needed - loop will naturally continue
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in captureFlow")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Process the raw audio data through effects and decoders.
     *
     * @param data The raw audio data
     * @return The processed audio data
     */
    private fun processAudioData(data: ByteArray): ByteArray {
        // First apply any audio effects from the AudioProcessor
        val processedData = audioProcessor.processAudio(data)

        // Then apply decoder if active
        val decoder = activeDecoder
        if (decoder != null) {
            try {
                val decodedData = decoder.decode(processedData)
                // Notify of decoded data via callback
                audioCaptureCallback?.onAudioDataDecoded(decodedData)
                // Return the decoded data
                return decodedData
            } catch (e: Exception) {
                Timber.e(e, "Error decoding audio data")
                // Return the processed but not decoded data if decoding fails
                return processedData
            }
        }

        // Return the processed (but not decoded) data
        return processedData
    }

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
     * @return A list of connected USB audio devices, or empty list if there was an error
     */
    /**
     * Get a list of connected USB audio devices.
     *
     * @param includeNonAudioDevices Whether to include non-audio USB devices in debug mode
     * @return A list of connected USB audio devices, or empty list if there was an error
     */
    fun getConnectedDevices(includeNonAudioDevices: Boolean = false): List<UsbDevice> {
        // First try to get audio devices
        val audioDevices = usbDeviceDiscovery.findAudioDevices().getOrElse { error ->
            Timber.e(error, "Error finding audio devices")
            emptyList() // Return empty list on error
        }

        // In debug mode, we can optionally include all USB devices for testing
        if (BuildConfig.DEBUG && includeNonAudioDevices && audioDevices.isEmpty()) {
            Timber.d("No audio devices found, fetching all USB devices for debug")
            return usbDeviceDiscovery.getAllConnectedDevices().getOrElse { error ->
                Timber.e(error, "Error finding all USB devices")
                emptyList()
            }
        }

        return audioDevices
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
            val hasPermission = usbPermissionManager.requestPermission(device)
            if (!hasPermission) {
                throw AudioException.PermissionDeniedException("Permission denied for device: ${device.deviceName}")
            }

            // Get USB manager from context
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            // Create and open the audio capture device
            val capture = UsbAudioCapture(usbManager, device)

            // Configure audio parameters if needed
            // capture.setAudioParameters(48000, 2, 16)  // Example: 48kHz, stereo, 16-bit

            // Open the device
            capture.open().getOrThrow()

            // Start the capture
            capture.startCapture().getOrThrow()

            // Store the audio capture instance
            audioCapture = capture
            isCapturing = true

            Timber.d("Audio capture started from device: ${device.deviceName}")
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
            val capture = audioCapture
            if (capture != null) {
                // Stop the capture
                capture.stopCapture().getOrThrow()

                // Close the device
                capture.close().getOrThrow()

                // Clear the reference
                audioCapture = null
            }

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
     * Check if there are any effects in the processing chain.
     *
     * @return True if there are effects, false otherwise
     */
    fun hasEffects(): Boolean {
        return audioProcessor.getEffects().isNotEmpty()
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
     * Clear all effects from the processing chain.
     *
     * @return This AudioWaveManager instance for method chaining
     */
    fun clearEffects(): AudioWaveManager {
        audioProcessor.clearEffects()
        return this
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

            // Close audio capture if it exists
            audioCapture?.close()
            audioCapture = null

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