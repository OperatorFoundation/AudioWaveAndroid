package org.operatorfoundation.audiowave.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.operatorfoundation.audiowave.utils.ErrorHandler
import timber.log.Timber

/**
 * Handles USB audio device discovery and detection.
 *
 * This class is responsible for finding USB audio devices connected to the Android device
 * and determining whether a USB device supports audio functionality.
 *
 * Features:
 * - Finds all connected USB audio devices
 * - Detects audio interfaces and audio class devices
 * - Provides detailed information about discovered devices
 * - Supports reactive programming with Flow-based API
 *
 * Example usage:
 * ```
 * val discovery = UsbDeviceDiscovery(context)
 *
 * // Get all audio devices
 * val audioDevices = discovery.findAudioDevices()
 *
 * // Reactive approach
 * discovery.audioDevicesFlow().collect { devices ->
 *     // Process newly discovered devices
 * }
 * ```
 */
class UsbDeviceDiscovery(private val context: Context) {

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
     * @return Result containing list of USB audio devices, or failure with error
     */
    fun findAudioDevices(): Result<List<UsbDevice>> {
        return ErrorHandler.runCatching {
            val audioDevices = mutableListOf<UsbDevice>()

            usbManager.deviceList.values.forEach { device ->
                if (isAudioDevice(device)) {
                    audioDevices.add(device)
                    Timber.d("Found audio device: ${device.deviceName}, product: ${device.productName}")
                }
            }

            audioDevices
        }
    }

    /**
     * Create a flow that emits connected USB audio devices.
     * This can be used to reactively process device discovery events.
     *
     * @param pollingIntervalMs How often to check for devices in milliseconds
     * @return Flow emitting lists of discovered audio devices
     */
    fun audioDevicesFlow(pollingIntervalMs: Long = 1000): Flow<List<UsbDevice>> = flow {
        while (true) {
            findAudioDevices().fold(
                onSuccess = { devices -> emit(devices) },
                onFailure = { error ->
                    Timber.e("Error finding audio devices: ${ErrorHandler.getErrorMessage(error)}")
                }
            )
            kotlinx.coroutines.delay(pollingIntervalMs)
        }
    }

    /**
     * Check if the device is an audio class device.
     *
     * @param device The USB device to check
     * @return true if it's an audio device, false otherwise
     */
    fun isAudioDevice(device: UsbDevice): Boolean {
        return ErrorHandler.runCatching {
            // Check each interface
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)

                // Check if it's an audio class interface
                if (intf.interfaceClass == AUDIO_CLASS) {
                    Timber.d("Found audio interface on device ${device.deviceName}: " +
                            "class=${intf.interfaceClass}, subclass=${intf.interfaceSubclass}")
                    return@runCatching true
                }
            }
            false
        }.getOrElse { error ->
            Timber.e("Error checking if device is audio device: ${ErrorHandler.getErrorMessage(error)}")
            false
        }
    }

    /**
     * Get detailed information about a USB device.
     *
     * @param device The USB device
     * @return Result containing a map with device information, or failure with error
     */
    fun getDeviceInfo(device: UsbDevice): Result<Map<String, String>> {
        return ErrorHandler.runCatching {
            val info = mutableMapOf<String, String>()

            info["deviceName"] = device.deviceName
            info["deviceId"] = device.deviceId.toString()
            info["vendorId"] = device.vendorId.toString()
            info["productId"] = device.productId.toString()

            device.productName?.let { info["productName"] = it }
            device.manufacturerName?.let { info["manufacturerName"] = it }

            info["interfaceCount"] = device.interfaceCount.toString()

            val interfaceInfo = StringBuilder()
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                interfaceInfo.append("Interface $i: class=${intf.interfaceClass}, ")
                interfaceInfo.append("subclass=${intf.interfaceSubclass}, ")
                interfaceInfo.append("endpoints=${intf.endpointCount}\n")
            }

            info["interfaces"] = interfaceInfo.toString()

            info
        }
    }

    /**
     * Get a list of all connected USB devices (not just audio devices).
     *
     * @return Result containing list of all connected USB devices, or failure with error
     */
    fun getAllConnectedDevices(): Result<List<UsbDevice>> {
        return ErrorHandler.runCatching {
            usbManager.deviceList.values.toList()
        }
    }

    /**
     * Get the USB manager instance.
     * This is useful for operations that need direct access to the USB system service.
     *
     * @return The USB manager
     */
    fun getUsbManager(): UsbManager {
        return usbManager
    }
}