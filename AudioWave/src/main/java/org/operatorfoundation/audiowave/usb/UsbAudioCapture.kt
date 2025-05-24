package org.operatorfoundation.audiowave.usb

import android.hardware.usb.*
import android.media.AudioFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.operatorfoundation.audiowave.exception.AudioException
import org.operatorfoundation.audiowave.utils.ErrorHandler
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UsbAudioCapture handles the low-level communication with USB audio devices.
 * It manages the connection, configuration, and reading of audio data.
 */
class UsbAudioCapture(private val usbManager: UsbManager, private val device: UsbDevice)
{
    companion object
    {
        private const val TAG = "UsbAudioCapture"

        // USB Audio Class constants
        private const val USB_CLASS_AUDIO = 1
        private const val USB_SUBCLASS_AUDIOSTREAMING = 2
        private const val USB_ENDPOINT_DIRECTION_IN = 0x80

        // Default audio parameters
        private const val DEFAULT_SAMPLE_RATE = 44100
        private const val DEFAULT_CHANNELS = 2
        private const val DEFAULT_BITS_PER_SAMPLE = 16
        private const val DEFAULT_BUFFER_SIZE = 4096
    }

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var usbEndpoint: UsbEndpoint? = null
    private val isCapturing = AtomicBoolean(false)

    // Audio configuration
    private var sampleRate = DEFAULT_SAMPLE_RATE
    private var channelCount = DEFAULT_CHANNELS
    private var bitsPerSample = DEFAULT_BITS_PER_SAMPLE
    private var bufferSize = DEFAULT_BUFFER_SIZE

    /**
     * Opens the USB device and configures it for audio capture.
     * Uses the centralized UsbAudioDetector for consistent device detection.
     *
     * @return Result with success or failure
     */
    suspend fun open(): Result<Unit> = withContext(Dispatchers.IO)
    {
        return@withContext ErrorHandler.runCatching {
            if (connection != null)
            {
                Timber.tag(TAG).w("Device already open")
                return@runCatching
            }

            Timber.tag(TAG).d("=== Opening USB Audio Device using centralized detector ===")

            // Open a connection to the USB device
            val conn = usbManager.openDevice(device)
                ?: throw AudioException.DeviceConnectionException("Could not open USB device")

            // Use centralized audio detection logic
            val audioConfig = UsbAudioDetector.findBestAudioConfiguration(device, TAG)
                ?: run {
                    conn.close()
                    throw AudioException.DeviceConfigurationException("No audio input endpoint found")
                }

            // Validate configuration
            if (audioConfig.inputEndpoint == null) {
                conn.close()
                throw AudioException.DeviceConfigurationException("Selected configuration has no input endpoint")
            }

            // Log the selected configuration
            Timber.tag(TAG).d("✅ Selected audio configuration:")
            Timber.tag(TAG).d("  ${audioConfig.getDescription()}")

            // Claim the interface
            if (!conn.claimInterface(audioConfig.usbInterface, true)) {
                conn.close()
                throw AudioException.DeviceConnectionException("Could not claim audio interface")
            }

            // Configure the device
            configureAudioDevice(conn, audioConfig)

            // Store the connection info
            connection = conn
            usbInterface = audioConfig.usbInterface
            usbEndpoint = audioConfig.inputEndpoint

            Timber.tag(TAG).d("✅ USB Audio device opened and configured: ${device.deviceName}")
            Timber.tag(TAG).d("   Quality: ${audioConfig.quality.displayName}")
            Timber.tag(TAG).d("   Strategy: ${audioConfig.strategy.displayName}")
            Timber.tag(TAG).d("=== USB Audio Device Setup Complete ===")
        }
    }

    /**
     * Starts capturing audio from the USB device.
     *
     * @return Result with success or failure
     */
    suspend fun startCapture(): Result<Unit> = withContext(Dispatchers.IO)
    {
        return@withContext ErrorHandler.runCatching {
            if (connection == null) {
                throw AudioException.DeviceConnectionException("Device not open")
            }

            if (isCapturing.get()) {
                Timber.w("Already capturing audio")
                return@runCatching
            }

            isCapturing.set(true)
            Timber.d("USB Audio capture started")
        }
    }

    /**
     * Stops capturing audio from the USB device.
     *
     * @return Result with success or failure
     */
    suspend fun stopCapture(): Result<Unit> = withContext(Dispatchers.IO)
    {
        return@withContext ErrorHandler.runCatching {
            isCapturing.set(false)
            Timber.d("USB Audio capture stopped")
        }
    }

    /**
     * Reads audio data from the USB device.
     *
     * @return ByteArray containing the audio data, or null if no data is available
     */
    suspend fun readAudioData(): Result<ByteArray> = withContext(Dispatchers.IO)
    {
        return@withContext ErrorHandler.runCatching {
            if (!isCapturing.get() || connection == null || usbEndpoint == null) {
                throw AudioException.AudioProcessingException("Not in capture state")
            }

            val conn = connection!!
            val endpoint = usbEndpoint!!

            // Create a buffer to hold the audio data
            val buffer = ByteBuffer.allocate(bufferSize)

            // Read from the USB endpoint
            val bytesRead = conn.bulkTransfer(
                endpoint,
                buffer.array(),
                buffer.capacity(),
                1000 // 1 second timeout
            )

            if (bytesRead <= 0) {
                throw AudioException.AudioProcessingException("Failed to read audio data: $bytesRead")
            }

            // Return the audio data
            return@runCatching buffer.array().copyOf(bytesRead)
        }
    }

    /**
     * Closes the USB connection and releases resources.
     */
    suspend fun close(): Result<Unit> = withContext(Dispatchers.IO)
    {
        return@withContext ErrorHandler.runCatching {
            isCapturing.set(false)

            val conn = connection
            val intf = usbInterface

            if (conn != null && intf != null) {
                try {
                    conn.releaseInterface(intf)
                } catch (e: Exception) {
                    Timber.e(e, "Error releasing interface")
                }

                try {
                    conn.close()
                } catch (e: Exception) {
                    Timber.e(e, "Error closing connection")
                }
            }

            connection = null
            usbInterface = null
            usbEndpoint = null

            Timber.d("USB Audio device closed: ${device.deviceName}")
        }
    }

    /**
     * Checks if the device is currently capturing audio.
     */
    fun isCapturing(): Boolean
    {
        return isCapturing.get()
    }

    /**
     * Configures the USB device for audio streaming.
     * Enhanced to use information from the audio configuration.
     */
    private fun configureAudioDevice(conn: UsbDeviceConnection, audioConfig: UsbAudioDetector.AudioConfiguration)
    {
        val intf = audioConfig.usbInterface

        // Log the configuration we're working with
        Timber.tag(TAG).d("Configuring audio device:")
        Timber.tag(TAG).d("  ${audioConfig.getDescription()}")

        // Try to set the interface (may not be needed for all devices)
        try {
            conn.setInterface(intf)
            Timber.tag(TAG).d("  Interface configuration applied")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not set interface configuration (may not be required)")
        }

        // Provide quality-specific configuration advice
        when (audioConfig.quality) {
            UsbAudioDetector.AudioQuality.PROFESSIONAL -> {
                Timber.tag(TAG).d("  Professional quality device detected - optimal configuration expected")
            }
            UsbAudioDetector.AudioQuality.STANDARD -> {
                Timber.tag(TAG).d("  Standard quality device - good audio performance expected")
            }
            UsbAudioDetector.AudioQuality.BASIC -> {
                Timber.tag(TAG).d("  Basic quality device - may need adjusted buffer sizes")
                // Could adjust buffer size here based on endpoint packet size
                adjustBufferSizeForEndpoint(audioConfig.inputEndpoint!!)
            }
            UsbAudioDetector.AudioQuality.EXPERIMENTAL -> {
                Timber.tag(TAG).w("  Experimental device - audio quality may vary")
                Timber.tag(TAG).w("  Consider using lower sample rates or adjusted buffer sizes")
                adjustBufferSizeForEndpoint(audioConfig.inputEndpoint!!)
            }
        }
        // FIXME:
        // 1. Set alternate settings for the interface (for bandwidth management)
        // 2. Send control transfers to configure sample rate
        // 3. Set up the audio format parameters
        // 4. Handle USB Audio Class specific descriptors

        Timber.tag(TAG).d("Audio device configured with: $sampleRate Hz, $channelCount channels, $bitsPerSample bits")
    }

    /**
     * Adjust buffer size based on endpoint characteristics for better compatibility
     */
    private fun adjustBufferSizeForEndpoint(endpoint: UsbEndpoint) {
        val packetSize = endpoint.maxPacketSize

        when {
            packetSize < 64 -> {
                // Very small packets - use smaller buffer to reduce latency
                bufferSize = minOf(bufferSize, 1024)
                Timber.tag(TAG).d("  Adjusted buffer size to $bufferSize for small packet endpoint")
            }
            packetSize > 512 -> {
                // Large packets - can use bigger buffer for efficiency
                bufferSize = maxOf(bufferSize, 8192)
                Timber.tag(TAG).d("  Adjusted buffer size to $bufferSize for large packet endpoint")
            }
            else -> {
                // Standard packet size - keep default buffer
                Timber.tag(TAG).d("  Using default buffer size $bufferSize for standard packet endpoint")
            }
        }
    }

    /**
     * Set the audio format parameters
     */
    fun setAudioParameters(
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        channelCount: Int = DEFAULT_CHANNELS,
        bitsPerSample: Int = DEFAULT_BITS_PER_SAMPLE
    ) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.bitsPerSample = bitsPerSample

        // Calculate a reasonable buffer size based on the audio parameters
        // Typically 20-50ms of audio data
        val bytesPerSample = bitsPerSample / 8
        val bytesPerFrame = bytesPerSample * channelCount
        val framesPerBuffer = sampleRate / 20  // 50ms buffer
        this.bufferSize = bytesPerFrame * framesPerBuffer

        Timber.d("Audio parameters set: $sampleRate Hz, $channelCount channels, $bitsPerSample bits")
    }

    /**
     * Get the current audio format as an Android AudioFormat
     */
    fun getAudioFormat(): AudioFormat {
        val encoding = when (bitsPerSample) {
            8 -> AudioFormat.ENCODING_PCM_8BIT
            16 -> AudioFormat.ENCODING_PCM_16BIT
            24 -> AudioFormat.ENCODING_PCM_24BIT_PACKED
            32 -> AudioFormat.ENCODING_PCM_32BIT
            else -> AudioFormat.ENCODING_PCM_16BIT
        }

        val channelConfig = when (channelCount) {
            1 -> AudioFormat.CHANNEL_IN_MONO
            2 -> AudioFormat.CHANNEL_IN_STEREO
            else -> AudioFormat.CHANNEL_IN_STEREO
        }

        return AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(encoding)
            .setChannelMask(channelConfig)
            .build()
    }
}