package org.operatorfoundation.audiowave

import org.operatorfoundation.audiowave.utils.AudioUtils
import org.operatorfoundation.audiowave.utils.ErrorHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import org.operatorfoundation.audiowave.effects.Effect
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Processes audio data with configurable effects chain.
 *
 * This class manages a chain of audio effects that can be applied to audio data in sequence,
 * allowing for complex audio processing pipelines.
 * Provides both synchronous and flow-based processing for streaming applications
 */
class AudioProcessor
{
    private val effects = CopyOnWriteArrayList<Effect>()

    /**
     * Process audio data through the effects chain.
     * For standard synchronous processing.
     *
     * @param audioData Raw audio data to process
     * @return Processed audio data
     */
    fun processAudio(audioData: ByteArray): ByteArray
    {
        return ErrorHandler.runCatching {
            // If no effects are active, return the original data
            if (effects.isEmpty())
            {
                return@runCatching audioData
            }

            // Apply effects chain
            applyEffectsChain(audioData)
        }.getOrElse { error ->
            Timber.e("Error processing audio: ${ErrorHandler.getErrorMessage(error)}")
            audioData // Return original data on error
        }
    }

    /**
     * Process audio data through the effects chain as a Flow.
     * For use by streaming applictaions where data is processed continuously.
     *
     * @param audioData Raw audio data to process
     * @return Flow emitting the processed audio data
     */
    fun processAudioFlow(audioData: ByteArray): Flow<ByteArray> = flow {
        // If no effects are active, emit the original data
        if (effects.isEmpty())
        {
            emit(audioData)
            return@flow
        }

        // Apply effects and emit the result
        emit(applyEffectsChain(audioData))
    }.catch { error ->
        // Log the error and emit the original data
        Timber.e("Error processing audio stream: ${ErrorHandler.getErrorMessage(error)}")
        emit(audioData)
    }

    /**
     * Process audio data as a continuous stream.
     * This allows for processing chunks of audio data in a reative way.
     *
     * @return Flow that accepts audio data and emits processed results
     */
    fun createAudioProcessingStream(): Flow<ByteArray> = flow {
        // TODO: Implementation to handle continuous stream processing
        // This could be implemented based on specific streaming requirements
        Timber.e("createAudioProcessingStream() is not implemented. Ignoring.")
    }

    /**
     * Apply the effects chain to the audio data.
     * Private helper method used by both synchronous and Flow-based processing.
     *
     * @param audioData Raw audio data to process
     * @return Processed audio data
     */
    fun applyEffectsChain(audioData: ByteArray): ByteArray
    {
        // Convert byte array to short array for processing
        var samples = AudioUtils.bytesToShorts(audioData)

        // Apply each effect in sequence
        var processedSamples = samples
        for (effect in effects)
        {
            if (effect.isEnabled())
            {
                try
                {
                    processedSamples = effect.process(processedSamples)
                }
                catch (error: Exception)
                {
                    // Log the error but continue with the effects chain
                    Timber.e(error, "Error applying effect: ${effect.name}")
                }
            }
        }

        // Convert back to byte array
        return AudioUtils.shortsToBytes(processedSamples)
    }

    /**
     * Add an effect to the processing chain.
     *
     * @param effect The effect to add
     * @return This AudioProcessor instance for method chaining
     */
    fun add(effect: Effect): AudioProcessor
    {
        if (!effects.contains(effect))
        {
            effects.add(effect)
            Timber.d("Effect added: ${effect.name} (${effect.id})")
        }
        return this
    }

    /**
     * Remove and effect from the processing chain by ID.
     *
     * @param effectId The ID of the effect to remove
     * @return This AudioProcessor instance for method chaining
     */
    fun remove(effectID: String): AudioProcessor
    {
        val sizeBefore = effects.size
        effects.removeIf { it.id == effectID }

        if (effects.size < sizeBefore) {
            Timber.d("Effect removed: $effectID")
        }

        return this
    }

    /**
     * Get a list of active effects.
     *
     * @return A list of the currently active effects.
     */
    fun getEffects(): List<Effect> { return effects.toList() }

    /**
     * Clear all effects.
     *
     * @return This AudioProcessor instance for method chaining
     */
    fun clearEffects(): AudioProcessor
    {
        effects.clear()
        Timber.d("All effects cleared.")
        return this
    }

}