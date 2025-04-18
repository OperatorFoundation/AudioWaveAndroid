package org.operatorfoundation.audiowave

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Manages USB device connection and audio stream handling.
 *
 * This internal class handles the low-level USB device operations, including:
 * - Device discovery and filtering for audio devices
 * - Permission requests and management
 * - Device connection and configuration
 * - Audio data streaming from the device
 *
 * Note: This is an internal implementation detail of the AudioWave library and
 * should not be used directly by client applications.
 */

internal class UsbDeviceManager(private val context: Context)
{

    companion object
    {
        private const val TAG = "UsbDeviceManager"
        private const val ACTION_USB_PERMISSION = "org.operatorfoundation.audiowave.USB_PERMISSION"

        // USB Audio Class Specifications
        private const val AUDIO_CLASS = 1                       // USB Audio Class code
        private const val SUBCLASS_AUDIOSTREAMING = 2           // Audio Streaming subclass

        // Audio Configuration
        private const val SAMPLE_RATE = 44100                   // Standard Audio Sample Rate (Hz)
        private const val BYTES_PER_SAMPLE = 2                  // 16-bit audio = 2 bytes per sample
        private const val CHANNELS = 2                          // Stereo audio
        private const val BUFFER_SIZE_MS = 50                   // Buffer size in milliseconds
        private const val MAX_PACKET_SIZE = 4096                // Maximum packet size for transfers
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbDevice: UsbDevice? = null
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inputEndpoint: UsbEndpoint? = null
    private var outputEndpoint: UsbEndpoint? = null

    private val isRunning = AtomicBoolean(false)
    private var audioThread: Thread? = null

    // Buffer sizes based on sampling rates
    // / 1000 converts from milliseconds to seconds since BUFFER_SIZE_MS is in milliseconds and SAMPLE_RATE is in samples per second
    private val bufferSize = (SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS * BUFFER_SIZE_MS) / 1000
    private val audioBuffer = (bufferSize * 2)  // Double buffer size for safety

    private var usbReceiver: BroadcastReceiver? = null

    // Map to store coroutine continuations for permission requests
    private var permissionContinuations = HashMap<String, (Boolean) -> Unit>()

    /**
     * Initialize USB device manager and register for USB events.
     * This suspending function can be called from a coroutine scope.
     *
     * @return True if initialization was successful, false otherwise
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO)
    {
        try {
            registerUsbReceiver()
            true
        } catch (error: Exception) {
            Log.e(TAG, "Failed to initialize UsbDeviceManager", error)
            false
        }
    }

    /**
     * Register broadcast receiver for USB events
     * This handles device attachment, detachment, and permission responses.
     */
    private fun registerUsbReceiver()
    {
        if (usbReceiver != null) return

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }

        usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent)
            {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        Log.d(TAG, "USB device attached: ${device?.deviceName}")
                    }

                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        if (device == usbDevice) {
                            Log.d(TAG, "Active USB device detached")
                            stopAudioStream()
                            releaseDevice()
                        }
                    }

                    ACTION_USB_PERMISSION -> {
                        val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                        device?.let {
                            val deviceID = it.deviceName

                            // Resume the coroutine that was waiting for permission
                            synchronized(permissionContinuations) {
                                permissionContinuations[deviceID]?.let { continuation ->
                                    continuation(granted && openUsbDevice(it))
                                    permissionContinuations.remove(deviceID)
                                }
                            }
                        }
                    }
                }
            }
        }

        context.registerReceiver(usbReceiver, filter)
    }

    /**
     * Get list of connected USB audio devices.
     * This filters all connected USB devices to find those that identify as audio devices.
     *
     * @return List of USB audio devices
     */
    fun getConnectedAudioDevices(): List<UsbDevice>
    {
        val audioDevices = mutableListOf<UsbDevice>()

        usbManager.deviceList.values.forEach { device ->
            if (isAudioDevice(device)) {
                audioDevices.add(device)
            }
        }

        return audioDevices
    }

    /**
     * Check if the device is an audio class device.
     * This determines if a USB device is an audio device by checking if any of its
     * interfaces have the USB Audio Class code.
     *
     * @param device The USB device to check
     * @return true if it's an audio device, false otherwise
     */
    private fun isAudioDevice(device: UsbDevice): Boolean
    {
        for (index in 0 until device.interfaceCount)
        {
            val intf = device.getInterface(index)
            if (intf.interfaceClass == AUDIO_CLASS) { return true }
        }

        return false
    }

    /**
     * Request permission to access the USB device.
     * This shows a system dialog to the user asking for permission.
     *
     * @param device The USB device to request permission for
     */
    private fun requestUsbPermission(device: UsbDevice)
    {
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(device, permissionIntent)
    }

    /**
     * Open a USB device for communication.
     * This suspending function can be called from a coroutine scope.
     *
     * @param device The USB device to open
     * @return True if the device was opened successfully, false otherwise
     */
    suspend fun openDevice(device: UsbDevice): Boolean = suspendCancellableCoroutine { continuation ->
        this.usbDevice = device

        if (!usbManager.hasPermission(device))
        {
            // Add the continuation to the map
            synchronized(permissionContinuations) {
                permissionContinuations[device.deviceName] = { granted ->
                    continuation.resume(granted)
                }
            }

            // Cancel handler to remove the continuation if needed
            continuation.invokeOnCancellation {
                synchronized(permissionContinuations) {
                    permissionContinuations.remove(device.deviceName)
                }
            }

            // Request permission
            requestUsbPermission(device)
        }
        else
        {
            val success = openUsbDevice(device)
            continuation.resume(success)
        }
    }

    /**
     * Open and configure the USB device after permission is granted.
     * This finds the audio interface and endpoints, and establishes the connection.
     *
     * @param device The USB device to open
     * @return true if successful, false otherwise
     */
    private fun openUsbDevice(device: UsbDevice): Boolean
    {
        // Find Audio Interface
        var audioInterface: UsbInterface? = null
        var inputEndpoint: UsbEndpoint? = null
        var outputEndpoint: UsbEndpoint? = null

        // Find the audio streaming interface
        for (index in 0 until device.interfaceCount)
        {
            val intf = device.getInterface(index)

            if (intf.interfaceClass == AUDIO_CLASS &&
                intf.interfaceSubclass == SUBCLASS_AUDIOSTREAMING)
            {
                audioInterface = intf

                // Find In and Out Endpoints
                for (i in 0 until intf.endpointCount)
                {
                    val endpoint = intf.getEndpoint(i)

                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC)
                    {
                        if (endpoint.direction == UsbConstants.USB_DIR_IN)
                        {
                            inputEndpoint = endpoint
                        }
                        else if (endpoint.direction == UsbConstants.USB_DIR_OUT)
                        {
                            outputEndpoint = endpoint
                        }
                    }
                }

                if (inputEndpoint != null) {
                    break  // Found what we need (Output Endpoint is optional)
                }
            }
        }

        // If we didn't find an audio streaming interface with an input endpoint,
        // try to find any audio interface with an input endpoint
        if (audioInterface == null || inputEndpoint == null)
        {
            for (index in 0 until device.interfaceCount)
            {
                val intf = device.getInterface(index)

                if (intf.interfaceClass == AUDIO_CLASS)
                {
                    for (i in 0 until intf.endpointCount)
                    {
                        val endpoint = intf.getEndpoint(i)

                        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                            endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK)
                        {
                            if (endpoint.direction == UsbConstants.USB_DIR_IN && inputEndpoint == null)
                            {
                                audioInterface = intf
                                inputEndpoint = endpoint
                            }
                            else if (endpoint.direction == UsbConstants.USB_DIR_OUT && outputEndpoint == null)
                            {
                                // If we already have an audio interface but no output endpoint,
                                // assign this one (might be on a different interface)
                                if (audioInterface != null)
                                {
                                    outputEndpoint = endpoint
                                }
                            }
                        }
                    }
                }
            }
        }

        if (audioInterface == null)
        {
            Log.e(TAG, "No suitable audio interface found")
            return false
        }

        // Input endpoint is REQUIRED - we can't proceed without it
        if (inputEndpoint == null)
        {
            Log.e(TAG, "No suitable audio input endpoint found")
            return false
        }

        // Talk about what we found!
        Log.d(TAG, "Found audio interface with input endpoint (max packet size: ${inputEndpoint.maxPacketSize})")

        if (outputEndpoint != null)
        {
            Log.d(TAG, "Also found output endpoint (max packet size: ${outputEndpoint.maxPacketSize})")
        }
        else
        {
            Log.d(TAG, "No output endpoint found - will operate in input-only mode")
        }

        // Open connection
        val connection = usbManager.openDevice(device)
        if (connection == null)
        {
            Log.e(TAG, "Could not open USB connection")
            return false
        }

        // Claim interface
        if (!connection.claimInterface(audioInterface, true))
        {
            Log.e(TAG, "Could not claim audio interface.")
            connection.close()
            return false
        }

        this.usbInterface = audioInterface
        this.inputEndpoint = inputEndpoint
        this.outputEndpoint = outputEndpoint
        this.usbConnection = connection

        return true
    }

    /**
     * Start capturing audio from the USB device.
     * This creates a background thread that continuously reads data from the device.
     *
     * @param processAudio Callback to process the captured audio data
     */
    fun startAudioStream(processAudio: (ByteArray) -> ByteArray)
    {
        if (isRunning.get()) { return }

        val connection = usbConnection ?: return
        val endpoint = inputEndpoint ?: return

        isRunning.set(true)

        audioThread = Thread {
            val packetSize = if (endpoint.maxPacketSize > 0) endpoint.maxPacketSize else MAX_PACKET_SIZE
            val buffer = ByteArray(packetSize)

            try {
                while (isRunning.get())
                {
                    // Use bulkTransfer for both bulk and isochronous endpoints
                    // TODO: (This is a simplification that works for many devices but isn't ideal)
                    val bytesRead = connection.bulkTransfer(endpoint, buffer, buffer.size, 100)

                    if (bytesRead > 0)
                    {
                        // Copy only the valid bytes
                        val audioData = buffer.copyOf(bytesRead)

                        // Process the audio data
                        val processedData = processAudio(audioData)

                        // Write to output endpoint if available
                        outputEndpoint?.let { outputEndpoint ->
                            connection.bulkTransfer(outputEndpoint, processedData, processedData.size, 100)
                        }
                    }
                }
            }
            catch (error: Exception)
            {
                Log.e(TAG, "Error in audio thread", error)
                isRunning.set(false)
            }
        }

        audioThread?.start()
    }

    /**
     * Stop the audio capture.
     * This stops the background thread and cleans up resources.
     */
    fun stopAudioStream()
    {
        isRunning.set(false)
        audioThread?.join(1000)
        audioThread = null
    }

    /**
     * Release the USB device and resources.
     * This releases the interface and closes the connection.
     */
    private fun releaseDevice()
    {
        usbInterface?.let { usbConnection?.releaseInterface(it) }
        usbConnection?.close()
        usbDevice = null
        usbInterface = null
        usbConnection = null
        inputEndpoint = null
        outputEndpoint = null
    }

    /**
     * Release all resources.
     * This stops audio capture, releases the device, and unregisters receivers.
     */
    fun release()
    {
        stopAudioStream()
        releaseDevice()

        usbReceiver?.let {
            context.unregisterReceiver(it)
            usbReceiver = null
        }

        // Clear any pending permission continuations
        synchronized(permissionContinuations) { permissionContinuations.clear() }
    }

}