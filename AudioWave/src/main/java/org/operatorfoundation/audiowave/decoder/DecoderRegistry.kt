package org.operatorfoundation.audiowave.decoder

import timber.log.Timber

/**
 * Registry for available audio decoders.
 *
 * This class manages the collection of available decoders and provides
 * methods to retrieve them by ID.
 *
 * Example usage:
 * ```
 * // Get a registry with default decoders
 * val registry = DecoderRegistry()
 *
 * // Register a custom decoder
 * registry.registerDecoder(MyCustomDecoder())
 *
 * // Get available decoders
 * val decoderIds = registry.getAvailableDecoders()
 *
 * // Get a specific decoder
 * val decoder = registry.getDecoder("radio_wave")
 * ```
 */
class DecoderRegistry
{
    private val decoders = HashMap<String, AudioDecoder>()

    init
    {
        // Register built-in decoders
        registerDecoder(RadioWaveDecoder())
        Timber.d("DecoderRegistry initialized with default decoders")
    }

    /**
     * Register a new decoder.
     *
     * @param decoder The decoder to register
     */
    fun registerDecoder(decoder: AudioDecoder)
    {
        decoders[decoder.id] = decoder
        Timber.d("Registered decoder: ${decoder.name} (${decoder.id})")
    }

    /**
     * Get a decoder by ID.
     *
     * @param id The ID of the decoder to retrieve
     * @return The decoder, or null if not found
     */
    fun getDecoder(id: String): AudioDecoder? {
        val decoder = decoders[id]
        if (decoder == null) {
            Timber.w("Decoder not found: $id")
        }
        return decoder
    }

    /**
     * Get a list of all available decoder IDs.
     *
     * @return A list of decoder IDs
     */
    fun getAvailableDecoders(): List<String> {
        return decoders.keys.toList()
    }

    /**
     * Get information about a decoder.
     *
     * @param id The ID of the decoder
     * @return A map containing decoder information, or null if the decoder was not found
     */
    fun getDecoderInfo(id: String): Map<String, String>? {
        val decoder = decoders[id] ?: return null

        return mapOf(
            "id" to decoder.id,
            "name" to decoder.name,
            "description" to decoder.description
        )
    }

}