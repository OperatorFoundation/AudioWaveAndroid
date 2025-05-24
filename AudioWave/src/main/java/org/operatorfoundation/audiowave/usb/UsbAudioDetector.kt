package org.operatorfoundation.audiowave.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import timber.log.Timber

/**
 * Centralized USB audio device detection and configuration utility.
 *
 * This class uses a progressive strategy approach to maximize compatibility with different
 * types of USB audio devices, from professional audio interfaces to development boards.
 *
 * The three-strategy approach ensures we find the best possible audio endpoints while
 * maintaining compatibility with both compliant and non-compliant USB audio devices.
 *
 * Basically:
 * High-end devices get the premium treatment (Strategy 1)
 * Standard devices work reliably (Strategy 2)
 * Quirky devices still have a chance (Strategy 3)
 * ;)
 */
object UsbAudioDetector
{

    private const val TAG = "UsbAudioDetector"

    // USB Audio Class constants
    const val USB_CLASS_AUDIO = 1
    const val USB_SUBCLASS_AUDIOCONTROL = 1
    const val USB_SUBCLASS_AUDIOSTREAMING = 2
    const val USB_SUBCLASS_MIDISTREAMING = 3

    /**
     * Represents a complete USB audio configuration with interface and endpoints.
     */
    data class AudioConfiguration(
        val usbInterface: UsbInterface,
        val inputEndpoint: UsbEndpoint?,
        val outputEndpoint: UsbEndpoint?,
        val strategy: DetectionStrategy,
        val quality: AudioQuality
    ) {
        /**
         * Get a human-readable description of this configuration
         */
        fun getDescription(): String {
            val inputDesc = inputEndpoint?.let {
                "${getEndpointTypeString(it.type)} IN (${it.maxPacketSize} bytes)"
            } ?: "None"

            val outputDesc = outputEndpoint?.let {
                "${getEndpointTypeString(it.type)} OUT (${it.maxPacketSize} bytes)"
            } ?: "None"

            return "Interface ${usbInterface.id} (class=${usbInterface.interfaceClass}, subclass=${usbInterface.interfaceSubclass}) - " +
                    "Input: $inputDesc, Output: $outputDesc - Strategy: ${strategy.name}, Quality: ${quality.name}"
        }
    }

    /**
     * Detection strategy used to find the configuration
     */
    enum class DetectionStrategy(val displayName: String) {
        AUDIO_STREAMING("USB Audio Streaming"),
        AUDIO_CLASS("USB Audio Class"),
        UNIVERSAL_FALLBACK("Universal Fallback")
    }

    /**
     * Quality assessment of the found configuration
     */
    enum class AudioQuality(val displayName: String) {
        PROFESSIONAL("Professional"),  // Isochronous endpoints, Audio Streaming interface
        STANDARD("Standard"),          // Audio Class with reasonable packet sizes
        BASIC("Basic"),               // Small packets or non-standard configuration
        EXPERIMENTAL("Experimental")   // Non-compliant devices, may not work well
    }

    /**
     * Find the best USB audio configuration for a device.
     * Uses progressive strategy to find the most suitable audio setup.
     *
     * @param device The USB device to analyze
     * @param logTag Optional tag for logging (defaults to class name)
     * @return AudioConfiguration if found, null if no suitable configuration exists
     */
    fun findBestAudioConfiguration(device: UsbDevice, logTag: String = TAG): AudioConfiguration? {
        Timber.tag(logTag).d("=== USB Audio Detection: ${device.deviceName} ===")
        Timber.tag(logTag).d("Product: ${device.productName}")
        Timber.tag(logTag).d("Vendor ID: 0x${device.vendorId.toString(16)}")
        Timber.tag(logTag).d("Analyzing ${device.interfaceCount} interfaces using progressive strategy...")

        // Try each strategy in order of preference
        findAudioStreamingConfiguration(device, logTag)?.let { return it }
        findAudioClassConfiguration(device, logTag)?.let { return it }
        findFallbackConfiguration(device, logTag)?.let { return it }

        Timber.tag(logTag).e("❌ No suitable audio configuration found after trying all strategies")
        return null
    }

    /**
     * Check if a device has any audio interfaces (quick check)
     */
    fun isAudioDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == USB_CLASS_AUDIO) {
                return true
            }
        }
        return false
    }

    // ========================================================================
    // STRATEGY 1: USB AUDIO STREAMING INTERFACES (Highest Priority)
    // ========================================================================

    private fun findAudioStreamingConfiguration(device: UsbDevice, logTag: String): AudioConfiguration? {
        Timber.tag(logTag).d("Strategy 1: Looking for USB Audio Streaming interfaces...")

        var bestConfig: AudioConfiguration? = null

        for (index in 0 until device.interfaceCount) {
            val intf = device.getInterface(index)

            if (intf.interfaceClass == USB_CLASS_AUDIO && intf.interfaceSubclass == USB_SUBCLASS_AUDIOSTREAMING) {
                Timber.tag(logTag).d("  ✓ Found Audio Streaming interface $index")

                var inputEndpoint: UsbEndpoint? = null
                var outputEndpoint: UsbEndpoint? = null

                // Analyze all endpoints in this interface
                for (i in 0 until intf.endpointCount) {
                    val endpoint = intf.getEndpoint(i)
                    logEndpoint(endpoint, i, logTag)

                    if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) {
                        if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                            // Prefer larger input endpoints for better audio quality
                            if (inputEndpoint == null || endpoint.maxPacketSize > inputEndpoint.maxPacketSize) {
                                inputEndpoint = endpoint
                                Timber.tag(logTag).d("      ★ Selected as INPUT endpoint (${endpoint.maxPacketSize} bytes)")
                            }
                        } else if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                            // Prefer larger output endpoints
                            if (outputEndpoint == null || endpoint.maxPacketSize > outputEndpoint.maxPacketSize) {
                                outputEndpoint = endpoint
                                Timber.tag(logTag).d("      ★ Selected as OUTPUT endpoint (${endpoint.maxPacketSize} bytes)")
                            }
                        }
                    }
                }

                // If we found at least an input endpoint, this is a valid configuration
                if (inputEndpoint != null)
                {
                    val config = AudioConfiguration(
                        usbInterface = intf,
                        inputEndpoint = inputEndpoint,
                        outputEndpoint = outputEndpoint,
                        strategy = DetectionStrategy.AUDIO_STREAMING,
                        quality = AudioQuality.PROFESSIONAL
                    )

                    // Keep the best configuration (prefer larger input packet sizes)
                    if (bestConfig == null ||
                        (inputEndpoint.maxPacketSize > (bestConfig.inputEndpoint?.maxPacketSize ?: 0))) {
                        bestConfig = config
                        Timber.tag(logTag).d("      ★★ New best configuration - Strategy 1")
                    }
                }
            }
        }

        bestConfig?.let {
            Timber.tag(logTag).d("✅ Strategy 1 success: ${it.getDescription()}")
        }

        return bestConfig
    }

    // ========================================================================
    // STRATEGY 2: ANY USB AUDIO CLASS INTERFACE (Medium Priority)
    // ========================================================================

    private fun findAudioClassConfiguration(device: UsbDevice, logTag: String): AudioConfiguration? {
        Timber.tag(logTag).d("Strategy 2: Looking for any USB Audio Class interfaces...")

        for (index in 0 until device.interfaceCount) {
            val intf = device.getInterface(index)

            if (intf.interfaceClass == USB_CLASS_AUDIO) {
                Timber.tag(logTag).d("  ✓ Found Audio Class interface $index (subclass: ${intf.interfaceSubclass})")

                for (i in 0 until intf.endpointCount) {
                    val endpoint = intf.getEndpoint(i)
                    logEndpoint(endpoint, i, logTag)

                    // Accept both isochronous and bulk endpoints with reasonable size
                    if ((endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                                endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) &&
                        endpoint.direction == UsbConstants.USB_DIR_IN &&
                        endpoint.maxPacketSize >= 64) {

                        // Look for output endpoint in the same interface
                        var outputEndpoint: UsbEndpoint? = null
                        for (j in 0 until intf.endpointCount) {
                            val outEndpoint = intf.getEndpoint(j)
                            if ((outEndpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC ||
                                        outEndpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) &&
                                outEndpoint.direction == UsbConstants.USB_DIR_OUT) {
                                outputEndpoint = outEndpoint
                                break
                            }
                        }

                        val quality = when {
                            endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC && endpoint.maxPacketSize >= 128 ->
                                AudioQuality.STANDARD
                            endpoint.maxPacketSize >= 128 ->
                                AudioQuality.STANDARD
                            else ->
                                AudioQuality.BASIC
                        }

                        val config = AudioConfiguration(
                            usbInterface = intf,
                            inputEndpoint = endpoint,
                            outputEndpoint = outputEndpoint,
                            strategy = DetectionStrategy.AUDIO_CLASS,
                            quality = quality
                        )

                        Timber.tag(logTag).d("✅ Strategy 2 success: ${config.getDescription()}")
                        return config
                    }
                }
            }
        }

        return null
    }

    // ========================================================================
    // STRATEGY 3: UNIVERSAL FALLBACK (Lowest Priority)
    // ========================================================================

    private fun findFallbackConfiguration(device: UsbDevice, logTag: String): AudioConfiguration? {
        Timber.tag(logTag).d("Strategy 3: Universal fallback - looking for any suitable endpoints...")

        for (index in 0 until device.interfaceCount) {
            val intf = device.getInterface(index)
            Timber.tag(logTag).d("  Checking interface $index (class: ${intf.interfaceClass})")

            for (i in 0 until intf.endpointCount) {
                val endpoint = intf.getEndpoint(i)

                // Very permissive: any bulk or isochronous input endpoint with minimal size
                if ((endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                            endpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) &&
                    endpoint.direction == UsbConstants.USB_DIR_IN &&
                    endpoint.maxPacketSize >= 4) {

                    // Look for output endpoint in the same interface
                    var outputEndpoint: UsbEndpoint? = null
                    for (j in 0 until intf.endpointCount) {
                        val outEndpoint = intf.getEndpoint(j)
                        if ((outEndpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                                    outEndpoint.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) &&
                            outEndpoint.direction == UsbConstants.USB_DIR_OUT) {
                            outputEndpoint = outEndpoint
                            break
                        }
                    }

                    val config = AudioConfiguration(
                        usbInterface = intf,
                        inputEndpoint = endpoint,
                        outputEndpoint = outputEndpoint,
                        strategy = DetectionStrategy.UNIVERSAL_FALLBACK,
                        quality = AudioQuality.EXPERIMENTAL
                    )

                    Timber.tag(logTag).d("✅ Strategy 3 success: ${config.getDescription()}")
                    Timber.tag(logTag).w("      ⚠ Using non-standard audio interface - audio quality may vary")
                    return config
                }
            }
        }

        return null
    }

    // ========================================================================
    // UTILITY METHODS
    // ========================================================================

    private fun logEndpoint(endpoint: UsbEndpoint, index: Int, logTag: String) {
        val direction = if (endpoint.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
        val type = getEndpointTypeString(endpoint.type)
        Timber.tag(logTag).d("    Endpoint $index: $direction $type (${endpoint.maxPacketSize} bytes)")
    }

    private fun getEndpointTypeString(type: Int): String {
        return when (type) {
            UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "CONTROL"
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISOCHRONOUS"
            UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
            UsbConstants.USB_ENDPOINT_XFER_INT -> "INTERRUPT"
            else -> "UNKNOWN($type)"
        }
    }

    /**
     * Get detailed device information for debugging purposes
     */
    fun getDeviceDebugInfo(device: UsbDevice): String {
        val builder = StringBuilder()
        builder.appendLine("=== USB Device Debug Info ===")
        builder.appendLine("Device: ${device.deviceName}")
        builder.appendLine("Product: ${device.productName}")
        builder.appendLine("Manufacturer: ${device.manufacturerName}")
        builder.appendLine("Vendor ID: 0x${device.vendorId.toString(16)} (${device.vendorId})")
        builder.appendLine("Product ID: 0x${device.productId.toString(16)} (${device.productId})")
        builder.appendLine("Device Class: ${device.deviceClass}")
        builder.appendLine("Interface Count: ${device.interfaceCount}")

        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            builder.appendLine("--- Interface $i ---")
            builder.appendLine("  Class: ${intf.interfaceClass}${if (intf.interfaceClass == USB_CLASS_AUDIO) " (AUDIO)" else ""}")
            builder.appendLine("  Subclass: ${intf.interfaceSubclass}")
            builder.appendLine("  Protocol: ${intf.interfaceProtocol}")
            builder.appendLine("  Endpoint Count: ${intf.endpointCount}")

            for (j in 0 until intf.endpointCount) {
                val endpoint = intf.getEndpoint(j)
                val direction = if (endpoint.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                val type = getEndpointTypeString(endpoint.type)
                builder.appendLine("    Endpoint $j: $direction $type (maxPacket: ${endpoint.maxPacketSize})")
            }
        }
        builder.appendLine("=== End Debug Info ===")

        return builder.toString()
    }
}