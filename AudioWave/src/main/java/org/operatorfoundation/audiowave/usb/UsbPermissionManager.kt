package org.operatorfoundation.audiowave.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.operatorfoundation.audiowave.exception.AudioException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.cancellation.CancellationException
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
 *
 * // Or observe permission changes using Flow
 * lifecycleScope.launch {
 *     permissionManager.permissionResults
 *         .filter { it.device.deviceId == usbDevice.deviceId }
 *         .collect { result ->
 *             if (result.granted) {
 *                 // Permission granted
 *             }
 *         }
 * }
 * ```
 */
class UsbPermissionManager(private val context: Context)
{
    companion object
    {
        private const val ACTION_USB_PERMISSION = "org.operatorfoundation.USB_PERMISSION"
    }

    /**
     * Data class representing a USB permission result
     */
    data class PermissionResult( val device: UsbDevice, val granted: Boolean)

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    // Mutex for thread-safe access to continuations map
    private val continuationsMutex = Mutex()
    private val permissionContinuations = mutableMapOf<String, Continuation<Boolean>>()

    // Flow of permission results for reactive style
    private val _permissionResults = MutableSharedFlow<PermissionResult>(extraBufferCapacity = 10)

    /**
     * Flow of permission results that can be collected to react to permission changes
     */
    val permissionresults: Flow<PermissionResult> = _permissionResults

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
     * @throws CancellationException if the coroutine is cancelled
     */
    suspend fun requestPermission(device: UsbDevice): Boolean
    {
        // Check if we already have permission
        if (usbManager.hasPermission(device)) { return true }

        // Using Flow API
        // This would allow reacting to permission changes for any device
        // but here we're just waiting for the specific device's result
        val deviceId = device.deviceId

        try
        {
            // Request permission
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                deviceId,
                Intent(ACTION_USB_PERMISSION).apply
                {
                    putExtra(UsbManager.EXTRA_DEVICE, device)
                },
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                }
                else { PendingIntent.FLAG_UPDATE_CURRENT }
            )

            usbManager.requestPermission(device, permissionIntent)
            Timber.d("Requested permission for device: ${device.deviceName}")

            // Traditional approach with suspendCancellableCoroutine
            return suspendCancellableCoroutine { continuation ->
                val deviceKey = deviceId.toString()

                // Store continuation for later resumption
                continuationsMutex.tryLock()
                try
                {
                    permissionContinuations[deviceKey] = continuation
                }
                finally
                {
                    if (continuationsMutex.isLocked)
                    {
                        continuationsMutex.unlock()
                    }
                }

                // Handle cancellation
                continuation.invokeOnCancellation {
                    Timber.d("Permission request for device $deviceKey was cancelled")
                    cleanupContinuation(deviceKey)
                }
            }
        }
        catch (e: Exception)
        {
            if (e is CancellationException) throw e
            Timber.e(e, "Error requesting permission to access a USB device.")
            throw AudioException.PermissionDeniedException("Failed to request permission", e)
        }
    }

    /**
     * Check if permission is already granted for a device.
     *
     * @param device The USB device to check
     * @return true if permission is granted, false otherwise
     */
    fun hasPermission(device: UsbDevice): Boolean  = usbManager.hasPermission(device)

    /**
     * Register the broadcast receiver for USB permission responses.
     */
    private fun registerPermissionReceiver() {
        if (permissionReceiver != null) return

        permissionReceiver = object : BroadcastReceiver()
        {
            override fun onReceive(context: Context, intent: Intent)
            {
                if (ACTION_USB_PERMISSION != intent.action) return

                val device = if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
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

                    // Emit to the Flow for reactive use cases
                    _permissionResults.tryEmit(PermissionResult(it, granted))

                    // Resume continuation if available
                    resumeContinuation(deviceKey, granted)
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
     * Resume and remove a continuation for a device
     */
    private fun resumeContinuation(deviceKey: String, result: Boolean)
    {
        continuationsMutex.tryLock()
        try
        {
            permissionContinuations.remove(deviceKey)?.resume(result)
        }
        finally
        {
            if (continuationsMutex.isLocked)
            {
                continuationsMutex.unlock()
            }
        }
    }

    /**
     * Cleanup a continuation without resuming it
     */
    private fun cleanupContinuation(deviceKey: String)
    {
        continuationsMutex.tryLock()
        try
        {
            permissionContinuations.remove(deviceKey)
        }
        finally
        {
            if (continuationsMutex.isLocked)
            {
                continuationsMutex.unlock()
            }
        }
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
        continuationsMutex.tryLock()
        try
        {
            permissionContinuations.keys.toList().forEach { deviceKey ->
                permissionContinuations.remove(deviceKey)?.resume(false)
                Timber.d("Completing pending permission request for device $deviceKey with 'false'")
            }
            permissionContinuations.clear()
        }
        finally
        {
            if (continuationsMutex.isLocked)
            {
                continuationsMutex.unlock()
            }
        }
    }
}