package org.operatorfoundation.audiowave.codec


/**
 * Base interface for audio codecs.
 *
 * An AudioCodec provides both encoding and decoding capabilities for a specific audio protocol of format.
 * It serves as a container for paired encoder and decoder implementations that handle the same protocol.
 *
 * Example implementation:
 * ```
 * class MyProtocolCodec : AudioCodec
 * {
 *      override val id: String = "my_protocol"
 *      override val name: String = "My Protocol"
 *      override val description: String = "Encodes and decodes My Protocol audio signals."
 *
 *      override val encoder: AudioEncoder = MyProtocolEncoder()
 *      override val decoder: AudioDecoder = MyProtocolDecoder()
 * }
 * ```
 */
interface AudioCodec
{
    /**
     * Unique ID for this codec
     */
    val id: String

    /**
     * Display name for this codec
     */
    val name: String

    /**
     * Description of what this codec does
     */
    val description: String

    /**
     * The encoder component of this codec
     */
    val encoder: AudioEncoder

    /**
     * The decoder component of this codec
     */
    val decoder: AudioDecoder

    /**
     * Configure the codec with specific parameters.
     * This allows runtime configuration of both encoder and decoder behavior.
     *
     * @param params Map of parameter names to parameter values
     */
    fun configure(params: Map<String, Any>)
    {
        // Default implementation configures both encoder and decoder
        encoder.configure(params)
        decoder.configure(params)
    }
}