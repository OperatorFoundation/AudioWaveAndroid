package org.operatorfoundation.audiowave.examples

import org.operatorfoundation.audiowave.AudioProcessor
import org.operatorfoundation.audiowave.Effect
import org.operatorfoundation.audiowave.device.UsbAudioDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Example demonstrating how to use the AudioProcessor with flows for streaming audio.
 */
class StreamingAudioExample(
    private val audioProcessor: AudioProcessor,
    private val usbDevice: UsbAudioDevice
) {
    /**
     * Start processing audio from the USB device.
     * This example shows how to set up a streaming pipeline for audio processing.
     */
    suspend fun startProcessing() = withContext(Dispatchers.IO) {
        // Set up the audio processor with effects
        audioProcessor
            .clearEffects()
            .addEffect(ReverbEffect("reverb_1"))
            .addEffect(EqualizerEffect("eq_1"))

        // Launch the streaming processing in a coroutine
        launch {
            // Create a flow from the USB audio device
            usbDevice.audioInputStream()
                // Process each chunk of audio data
                .map { audioChunk ->
                    // Use the Flow-based audio processing
                    audioProcessor.processAudioFlow(audioChunk)
                }
                // Flatten the nested flow
                .flattenFlow()
                // Run processing on IO dispatcher
                .flowOn(Dispatchers.IO)
                // Handle each processed audio chunk
                .onEach { processedAudio ->
                    // Send the processed audio back to the output device
                    usbDevice.writeAudio(processedAudio)
                }
                // Log any errors but keep the flow going
                .catch { error ->
                    Timber.e(error, "Error in audio processing stream")
                }
                // Collect the flow to start processing
                .collect()
        }
    }

    /**
     * Stop the audio processing.
     */
    fun stopProcessing() {
        // Implementation to stop the processing pipeline
    }

    /**
     * Helper extension function to flatten nested flows.
     */
    private fun <T> Flow<Flow<T>>.flattenFlow(): Flow<T> = flow {
        collect { innerFlow ->
            innerFlow.collect { value ->
                emit(value)
            }
        }
    }
}

/**
 * Example effect implementations for demonstration.
 */
class ReverbEffect(override val id: String) : Effect {
    override val name: String = "Reverb Effect"
    private var enabled = true

    override fun isEnabled(): Boolean = enabled

    override fun process(samples: ShortArray): ShortArray {
        // Real implementation would add reverb effect
        return samples
    }
}

class EqualizerEffect(override val id: String) : Effect {
    override val name: String = "Equalizer"
    private var enabled = true

    override fun isEnabled(): Boolean = enabled

    override fun process(samples: ShortArray): ShortArray {
        // Real implementation would apply EQ settings
        return samples
    }
}