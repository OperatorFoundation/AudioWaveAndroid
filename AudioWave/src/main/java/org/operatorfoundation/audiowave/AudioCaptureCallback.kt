package org.operatorfoundation.audiowave

/**
 * Callback interface for receiving audio data from the AudioWave library.
 *
 * This interface provides callbacks for both raw audio data captured from the USB device
 * and decoded data if a decoder is active.
 *
 * Note: For reactive programming, consider using the Flow-based API instead.
 *
 * Example implementation:
 * ```
 * class MyAudioHandler : AudioCaptureCallback {
 *     override fun onAudioDataCaptured(data: ByteArray) {
 *         // Handle raw audio data
 *         // Note: This is called on a background thread
 *
 *         // Example: Calculate audio level
 *         val level = calculateAudioLevel(data)
 *
 *         // Post to UI thread if updating UI
 *         runOnUiThread {
 *             updateLevelMeter(level)
 *         }
 *     }
 *
 *     override fun onAudioDataDecoded(data: ByteArray) {
 *         // Handle decoded data
 *         // This is called only if a decoder is active
 *
 *         // Example: Process decoded signal data
 *         processDecodedSignal(data)
 *     }
 * }
 * ```
 *
 * Important: The callback methods are invoked on a background thread.
 * If you need to update UI components, make sure to post to the main thread.
 */
interface AudioCaptureCallback {
    /**
     * Called when raw audio data is captured from the USB device.
     * This method is invoked on a background thread.
     *
     * @param data The raw audio data as a byte array (typically 16-bit PCM data)
     */
    fun onAudioDataCaptured(data: ByteArray)

    /**
     * Called when audio data has been decoded (if a decoder is active).
     * This method is invoked on a background thread.
     *
     * @param data The decoded data as a byte array (format depends on the decoder)
     */
    fun onAudioDataDecoded(data: ByteArray)
}