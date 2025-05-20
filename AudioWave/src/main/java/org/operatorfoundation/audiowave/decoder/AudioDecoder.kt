package org.operatorfoundation.audiowave.decoder

/**
 * Base interface for audio decoders.
 *
 * Audio decoders transform raw audio data into meaningful information.
 * This can include demodulating AM/FM signals, decoding digital protocols,
 * or extracting specific information from audio streams.
 *
 * To create a custom decoder:
 * 1. Implement this interface
 * 2. Register the decoder with DecoderRegistry
 *
 * Example implementation:
 * ```
 * class MyCustomDecoder : AudioDecoder {
 *     override val id: String = "my_decoder"
 *     override val name: String = "My Custom Decoder"
 *     override val description: String = "Decodes XYZ protocol signals"
 *
 *     override fun decode(audioData: ByteArray): ByteArray {
 *         // Transform the audio data into meaningful information
 *         return processedData
 *     }
 *
 *     override fun configure(params: Map<String, Any>) {
 *         // Configure decoder parameters
 *     }
 * }
 * ```
 */
 interface AudioDecoder
{
    /**
     * Unique ID for this decoder
     */
    val id: String

    /**
     * Display name for this decoder
     */
    val name: String

    /**
     * Description of what this decoder does
     */
    val description: String

    /**
     * Decode the audio data.
     * This method should transform the raw audio data into meaningful information.
     *
     * @param audioData The raw audio data to decode
     * @return The decoded data
     */
    fun decode(audioData: ByteArray): ByteArray

    /**
     * Configure the decoder with specific parameters.
     * This allows runtime configuration of decoder behavior.
     *
     * @param params Map of parameter names to parameter values
     */
    fun configure(params: Map<String, Any>)
}