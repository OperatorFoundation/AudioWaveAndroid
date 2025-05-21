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
     *
     * @return Result with success or failure
     */
    suspend fun open(): Result<Unit> = withContext(Dispatchers.IO)
    {
        return@withContext ErrorHandler.runCatching {
            if (connection != null)
            {
                Timber.w("Device already open")
                return@runCatching
            }

            // Open a connection to the USB device
            val conn = usbManager.openDevice(device)
                ?: throw AudioException.DeviceConnectionException("Could not open USB device")

            // Find the audio interface
            val audioInterface = findAudioInterface()
                ?: throw AudioException.DeviceConfigurationException("No audio interface found")

            // Find the input endpoint
            val endpoint = findAudioEndpoint(audioInterface)
                ?: throw AudioException.DeviceConfigurationException("No audio input endpoint found")

            // Claim the interface
            if (!conn.claimInterface(audioInterface, true)) {
                conn.close()
                throw AudioException.DeviceConnectionException("Could not claim audio interface")
            }

            // Configure the device
            configureAudioDevice(conn, audioInterface)

            // Store the connection info
            connection = conn
            usbInterface = audioInterface
            usbEndpoint = endpoint

            Timber.d("USB Audio device opened and configured: ${device.deviceName}")
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
     * Finds the audio interface on the USB device.
     */
    private fun findAudioInterface(): UsbInterface?
    {
        for (i in 0 until device.interfaceCount)
        {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == USB_CLASS_AUDIO)
            {
                return intf
            }
        }
        return null
    }

    /**
     * Finds an audio input endpoint on the specified interface.
     */
    private fun findAudioEndpoint(intf: UsbInterface): UsbEndpoint?
    {
        for (i in 0 until intf.endpointCount) {
            val endpoint = intf.getEndpoint(i)
            // Check for input (IN) endpoint
            if (endpoint.direction == UsbConstants.USB_DIR_IN &&
                endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC)
            {
                return endpoint
            }
        }
        return null
    }

    /**
     * Configures the USB device for audio streaming.
     * This involves sending control transfers to set up the audio format.
     */
    private fun configureAudioDevice(conn: UsbDeviceConnection, intf: UsbInterface)
    {
        // In a real implementation, this would involve:
        // 1. Setting the alternate setting for the interface
        // 2. Configuring the sample rate
        // 3. Setting up the audio format

        // For simplicity, we'll assume the device is already configured
        // But in a real implementation, you would need to send the appropriate control transfers

        // Example: Set alternate setting for better bandwidth
        try {
            conn.setInterface(intf)
        } catch (e: Exception) {
            Timber.w(e, "Error setting interface")
        }

        Timber.d("Audio device configured with: $sampleRate Hz, $channelCount channels, $bitsPerSample bits")
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