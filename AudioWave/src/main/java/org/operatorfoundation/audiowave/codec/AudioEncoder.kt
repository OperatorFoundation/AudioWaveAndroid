package org.operatorfoundation.audiowave.codec

/**
 * Base interface for audio encoders.
 *
 * Audio encoders transform input data into audio signals following specific protocols.
 * This can include modulating digital information into AM/FM signals,
 * encoding digital protocols, or generating specific audio patterns.
 *
 * To create a custom encoder:
 * 1. Implement this interface
 * 2. Register it with a codec in the CodecRegistry
 *
 * Example implementation:
 * ```
 * class MyCustomEncoder : AudioEncoder
 * {
 *      override val id: String = "MyEncoder"
 *      override val name: String = "My Custom Encoder"
 *      override val description: String = "Encodes data into XYZ protocol signals"
 *
 *      override fun encode(data: ByteArray): ByteArray
 *      {
 *          // Transform the data into an audio signal
 *          return audioData
 *      }
 *
 *      override fun configure(params: Map<String, Any>)
 *      {
 *          // Configure encoder parameters
 *      }
 * }
 * ```
 */
interface AudioEncoder
{
    /**
     * Unique ID for this encoder
     */
    val id: String

    /**
     * Display name for this encoder
     */
    val name: String

    /**
     * Description of what this encoder does
     */
    val description: String

    /**
     * Encode the input data.
     * This method transforms the input data into audio following a specific protocol.
     *
     * @param data The data to encode
     * @return The audio data (PCM format)
     */
    fun encode(data: ByteArray): ByteArray

    /**
     * Configure the encoder with specific parameters.
     * This allows runtime configuration of encoder behavior.
     *
     * @param params Map of parameter names to parameter values
     */
    fun configure(params: Map<String, Any>)
}