package org.operatorfoundation.audiowave.codec.codecs.wspr

import org.operatorfoundation.audiowave.codec.AudioCodec
import org.operatorfoundation.audiowave.codec.AudioDecoder
import org.operatorfoundation.audiowave.codec.AudioEncoder
import timber.log.Timber

/**
 * Codec for WSPR (Weak Signal Propagation Reporter) protocol.
 *
 * This codec provides both encoding and decoding capabilities for WSPR signals.
 * WSPR is a digital protocol designed for weak-signal communication on amateur radio frequencies,
 * using a very narrow bandwidth.
 *
 * Features:
 *  - Encodes callsign, grid locator, and power level into WSPR audio signals
 *  - Decodes WSPR audio signals to extract callsign, grid locator, and power information
 *  - Handles 4-FSK modulation with proper timing and frequencies
 *
 *  Example usage:
 *  ```
 *  // Get the WSPR codec
 *  val wsprCodec = codecRegistry.getCodec("wspr") as WsprCodec
 *
 *  // Configure it
 *  wsprCodec.configure(mapOf(
 *      "centerFrequency" to 1500.0f,
 *      "signalThreshold" to 0.2f
 *  ))
 *
 *  // Encode WSPR message
 *  val callsign = "KD2XYZ"
 *  val grid = "FN20"
 *  powerDbm = 10
 *  val encodedData = wsprCodec.encodeWsprMessage(callsign, grid, powerDbm)
 *
 *  // Decode WSPR audio
 *  val decodedMessages = wsprCodec.decoder.decode(wsprAudio)
 *  ```
 */
class WsprCodec : AudioCodec
{
    override val id: String = "wspr"
    override val name: String = "WSPR Codec"
    override val description: String ="Encodes and decodes Weak Signal Propogation Reporter (WSPR) signals."

    // create the encoder and decoder
    override val decoder: AudioDecoder = WsprDecoder()
    override val encoder: AudioEncoder = WsprEncoder()

    /**
     * Convenience method to encode WSPR message with specific parameters.
     *
     * @param callsign Amateur radio callsign
     * @param grid Maidenhead grid locator (4 chars)
     * @param powerDbm Power level in dBm (0-60)
     * @return ByteArray containing audio data
     */
    fun encodeWsprMessage(callsign: String, grid: String, powerDbm: Int): ByteArray
    {
        // Create the message data
        val messageString = "$callsign|$grid|$powerDbm"
        val messageData = messageString.toByteArray()

        // Use the encoder to generate audio data
        return encoder.encode(messageData)
    }

    /**
     * Calculate seconds until next WSPR transmit window.
     * WSPR transmissions should start at even numbered minutes.
     *
     * @return Number of seconds until the next transmit window
     */
    fun secondsUntilNextTransmitWindow(): Int
    {
        // Delegate to the encoder implementation
        return return (encoder as WsprEncoder).secondsUntilNextTransmitWindow()
    }

    /**
     * Check if the current time is appropriate for WSPR transmission.
     *
     * @return True if within the start window for WSPR
     */
    fun isWsprTransmitTime(): Boolean
    {
        // Delegate to the encoder implementation
        return (encoder as WsprEncoder).isWsprTransmitTime()
    }

    /**
     * Configure both encoder and decoder with the same parameters.
     * This is a convenience method that delegates to the base configure method.
     *
     * @param params Map of parameter names to parameter values
     */
    override fun configure(params: Map<String, Any>) {
        super.configure(params)
        Timber.d("Configured WSPR codec with parameters: $params")
    }
}
