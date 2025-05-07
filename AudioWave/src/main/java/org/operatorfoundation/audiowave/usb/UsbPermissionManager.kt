package org.operatorfoundation.audiowave.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import org.operatorfoundation.audiowave.exception.AudioException
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages USB permission requests and responses.
 *
 * This class handles the process of requesting permission to access USB devices
 * and receiving the system's response to those requests.
 *
 * Features:
 * - Asynchronous permission requests using coroutines
 * - Automatic broadcast receiver management
 * - Support for multiple simultaneous permission requests
 * - Proper cancellation handling
 *
 * Example usage:
 * ```
 * val permissionManager = UsbPermissionManager(context)
 *
 * // Request permission using coroutines
 * try {
 *     val granted = permissionManager.requestPermission(usbDevice)
 *     if (granted) {
 *         // Permission granted, proceed with device access
 *     } else {
 *         // Permission denied by user
 *     }
 * } catch (e: Exception) {
 *     // Handle errors
 * }
 * ```
 */
class UsbPermissionManager(private val context: Context)
{
    companion object
    {
        private const val ACTION_USB_PERMISSION = "org.operatorfoundation.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val permissionContinuations = ConcurrentHashMap<String, Continuation<Boolean>>()
    private var permissionReceiver: BroadcastReceiver? = null

    init { registerPermissionReceiver() }

    /**
     * Request permission to access a USB device.
     *
     * This suspending function will show a system dialogue asking the user for permission to
     * access the specified USB device. It resumes when the user responds or when an error occurs.
     *
     * @param device The USB device to request permission for
     * @return True if permission was granted, false if it was denied
     * @throws AudioException.PermissionDeniedException if permission request fails
     */
    suspend fun requestPermission(device: UsbDevice): Boolean = suspendCancellableCoroutine { continuation ->
        try
        {
            // Check if we already have permission
            if (usbManager.hasPermission(device))
            {
                continuation.resume(true)
                return@suspendCancellableCoroutine
            }

            // Store the continuation for the permission response
            val deviceKey = device.deviceId.toString()
            permissionContinuations[deviceKey] = continuation

            // Set up the cancellation handler
            continuation.invokeOnCancellation {
                permissionContinuations.remove(deviceKey)
                Timber.d("Permission request for device $deviceKey was cancelled")
            }

            // Request permission
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                device.deviceId,
                Intent(ACTION_USB_PERMISSION).apply { putExtra(UsbManager.EXTRA_DEVICE, device) },
                PendingIntent.FLAG_IMMUTABLE
            )

            usbManager.requestPermission(device, permissionIntent)
            Timber.d("Requested permission for device: ${device.deviceName}")
        }
        catch (error: Exception)
        {
            Timber.e(error, "Error requesting permission to access a USB device.")

            permissionContinuations.remove(device.deviceId.toString())
            continuation.resumeWithException(AudioException.PermissionDeniedException("Failed to request permission", error))
        }
    }

    /**
     * Check if permission is already granted for a device.
     *
     * @param device The USB device to check
     * @return true if permission is granted, false otherwise
     */
    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    /**
     * Register the broadcast receiver for USB permission responses.
     */
    private fun registerPermissionReceiver() {
        if (permissionReceiver != null) return

        permissionReceiver = object : BroadcastReceiver()
        {
            override fun onReceive(context: Context, intent: Intent)
            {
                if (ACTION_USB_PERMISSION == intent.action)
                {
                    val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                    {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    }
                    else
                    {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    device?.let {
                        val deviceKey = it.deviceId.toString()
                        Timber.d("Received permission result for device $deviceKey: $granted")

                        // Resume the waiting coroutine with the permission result
                        permissionContinuations[deviceKey]?.let { continuation ->
                            continuation.resume(granted)
                        }
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        Timber.d("USB permission receiver registered")
    }

    /**
     * Clean up resources used by the permission manager.
     */
    fun release()
    {
        // Unregister the broadcast receiver
        permissionReceiver?.let {
            try
            {
                context.unregisterReceiver(it)
                Timber.d("USB permission receiver unregistered")
            }
            catch (error: Exception)
            {
                Timber.e(error, "Error received while unregistering USB permission receiver.")
            }
            permissionReceiver = null
        }

        // Complete pending permission requests
        permissionContinuations.keys.forEach { deviceKey ->
            permissionContinuations[deviceKey]?.let { continuation ->
                continuation.resume(false)
                Timber.d("Completing pending permission request for device $deviceKey with 'false'")
            }
        }
        permissionContinuations.clear()
    }
}