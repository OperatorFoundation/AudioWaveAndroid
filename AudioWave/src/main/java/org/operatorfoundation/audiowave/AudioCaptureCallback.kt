package org.operatorfoundation.audiowave

/**
 * Callback interface for receiving audio data from the AudioWave library.
 *
 * This interface provides callbacks for both raw audio data captured from the USB device
 * and decoded data if a decoder is active. Implement this interface to receive
 * and process audio data in your application.
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
 *
 *     private fun calculateAudioLevel(data: ByteArray): Float {
 *         // Calculate RMS audio level
 *         var sum = 0.0
 *         var count = 0
 *
 *         for (i in 0 until data.size - 1 step 2) {
 *             if (i + 1 < data.size) {
 *                 // Convert two bytes to a 16-bit sample
 *                 val sample = (data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)
 *                 sum += sample * sample
 *                 count++
 *             }
 *         }
 *
 *         if (count == 0) return 0f
 *
 *         val rms = Math.sqrt(sum / count)
 *         // Normalize to 0.0 - 1.0 range (16-bit audio has range -32768 to 32767)
 *         return (rms / 32768.0).toFloat()
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