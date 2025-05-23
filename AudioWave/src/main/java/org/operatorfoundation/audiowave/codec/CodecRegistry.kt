package org.operatorfoundation.audiowave.codec

import timber.log.Timber

/**
 * Registry for available audio codecs.
 *
 * This class manages the collection of available codecs and provides
 * methods to retrieve them by ID. Each codec contains both an encoder
 * and a decoder for a specific audio protocol.
 *
 * Example usage:
 * ```
 * // Get a registry with default codecs
 * val registry = CodecRegistry()
 *
 * // Register a custom codec
 * registry.registerCodec(MyCustomCodec())
 *
 * // Get available codecs
 * val codecIds = registry.getAvailableCodecs()
 *
 * // Get a specific codec
 * val codec = registry.getCodec("wspr")
 *
 * // Get a specific encoder or decoder
 * val encoder = registry.getEncoder("wspr")
 * val decoder = registry.getDecoder("wspr")
 * ```
 */
class CodecRegistry
{
    private val codecs = HashMap<String, AudioCodec>()

    init
    {
        // Register built-in codecs
        // This will be populated as codec implementations are developed
        Timber.d("CodecRegistry initialized")
    }

    /**
     * Register a new codec.
     *
     * @param codec The codec to register
     */
    fun registerCodec(codec: AudioCodec) {
        codecs[codec.id] = codec
        Timber.d("Registered codec: ${codec.name} (${codec.id})")
    }

    /**
     * Get a codec by ID.
     *
     * @param id The ID of the codec to retrieve
     * @return The codec, or null if not found
     */
    fun getCodec(id: String): AudioCodec?
    {
        val codec = codecs[id]

        if (codec == null)
        {
            Timber.w("Codec not found: $id")
        }

        return codec
    }

    /**
     * Get an encoder by codec ID.
     *
     * @param id The ID of the codec containing the encoder
     * @return The encoder, or null if not found
     */
    fun getEncoder(id: String): AudioEncoder?
    {
        return getCodec(id)?.encoder
    }

    /**
     * Get a list of all available codec IDs.
     *
     * @return A list of codec IDs
     */
    fun getDecoder(id: String): AudioDecoder?
    {
        return getCodec(id)?.decoder
    }

    /**
     * Get a list of all available codec IDs.
     *
     * @return A list of codec IDs
     */
    fun getAvailableCodecs(): List<String>
    {
        return codecs.keys.toList()
    }

    /**
     * Get information about a codec.
     *
     * @param id The ID of the codec
     * @return A map containing codec information, or null if the codec was not found
     */
    fun getCodecInfo(id: String): Map<String, String>?
    {
        val codec = codecs[id] ?: return null

        return mapOf(
            "id" to codec.id,
            "name" to codec.name,
            "description" to codec.description,
            "encoderId" to codec.encoder.id,
            "decoderId" to codec.decoder.id
        )
    }
}