package org.operatorfoundation.audiowave.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import timber.log.Timber

/**
 * Handles USB device discovery and detection.
 *
 * This class is responsible for finding USB audio devices connected to the Android device
 * and determining whether a USB device supports audio functionality.
 *
 * Features:
 * - Finds all connected USB audio devices
 * - Detects audio interfaces and audio class devices
 * - Provides detailed information about discovered devices
 *
 * Example usage:
 * ```
 * val discovery = UsbDeviceDiscovery(context)
 * val audioDevices = discovery.findAudioDevices()
 *
 * audioDevices.forEach { device ->
 *     println("Found audio device: ${device.deviceName}")
 * }
 * ```
 */
class UsbDeviceDiscovery(private val context: Context)
{
    companion object {
        // USB Audio Class specifications
        const val AUDIO_CLASS = 1                // USB Audio Class code
        const val AUDIO_SUBCLASS_CONTROL = 1     // Audio Control subclass
        const val AUDIO_SUBCLASS_STREAMING = 2   // Audio Streaming subclass
        const val AUDIO_SUBCLASS_MIDI = 3        // MIDI Streaming subclass
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    /**
     * Find all connected USB audio devices.
     *
     * @return List of USB audio devices
     */
    fun findAudioDevices(): List<UsbDevice>
    {
        val audioDevices = mutableListOf<UsbDevice>()

        try {
            usbManager.deviceList.values.forEach { device ->
                if (isAudioDevice(device))
                {
                    audioDevices.add(device)
                    Timber.d("Found audio device: ${device.deviceName}, product: ${device.productName}")
                }
            }
        }
        catch (error: Exception)
        {
            Timber.e(error, "Error finding audio devices.")
        }

        return audioDevices
    }

    /**
     * Check if the device is an audio class device.
     *
     * @param device The USB device to check
     * @return true if it's an audio device, false otherwise
     */
    fun isAudioDevice(device: UsbDevice): Boolean
    {
        try {
            // Check each interface
            for (i in 0 until device.interfaceCount)
            {
                val intf = device.getInterface(i)

                // Check if it's an audio class interface
                if (intf.interfaceClass == AUDIO_CLASS)
                {
                    Timber.d("Found audio interface on device ${device.deviceName}: " +
                            "class=${intf.interfaceClass}, subclass=${intf.interfaceSubclass}")
                    return true
                }
            }
        }
        catch (error: Exception)
        {
            Timber.e(error, "Error checking if a device is an audio device: ${device.deviceName}")
        }

        return false
    }

    /**
     * Get detailed information about a USB device.
     *
     * @param device The USB device
     * @return A map containing detailed information about the device
     */
    fun getDeviceInfo(device: UsbDevice): Map<String, String>
    {
        val info = mutableMapOf<String, String>()

        info["deviceName"] = device.deviceName
        info["deviceId"] = device.deviceId.toString()
        info["vendorID"] = device.vendorId.toString()

        device.productName?.let { info["productName"] = it }
        device.manufacturerName?.let { info["manufacturerName"] = it }

        info["interfaceCount"] = device.interfaceCount.toString()

        val interfaceInfo = StringBuilder()
        for (i in 0 until device.interfaceCount)
        {
            val intf = device.getInterface(i)
            interfaceInfo.append("Interface $i: class=${intf.interfaceClass}, ")
            interfaceInfo.append("subclass=${intf.interfaceSubclass}, ")
            interfaceInfo.append("endpoints=${intf.endpointCount}\n")
        }

        info["interfaces"] = interfaceInfo.toString()

        return info
    }

    /**
     * Get a list of all interface devices (not just audio devices).
     *
     * @return List of all connected USB devices
     */
    fun getAllConnectedDevices(): List<UsbDevice>
    {
        return usbManager.deviceList.values.toList()
    }

    /**
     * Get the USB manager instance.
     * This is useful for operations that need direct access to the USB system service.
     *
     * @return The USB manager
     */
    fun getUsbManager(): UsbManager
    {
        return usbManager
    }
}