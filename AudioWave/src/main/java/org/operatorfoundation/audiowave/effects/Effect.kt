package org.operatorfoundation.audiowave.effects

/**
 * Base interface for audio effects.
 *
 * Audio effects modify audio samples in some way. They can be chained together
 * to create complex audio processing pipelines.
 *
 * The AudioWave library includes several built-in effects:
 * - GainEffect: Controls the volume of the audio
 * - EchoEffect: Adds echo/delay to the audio
 *
 * To create a custom effect:
 * 1. Implement this interface
 * 2. Add your effect to the AudioWaveManager using addEffect()
 *
 * Example implementation:
 * ```
 * class MyCustomEffect : Effect {
 *     override val id: String = "my_effect"
 *     override val name: String = "My Custom Effect"
 *     private var enabled = true
 *
 *     override fun process(samples: ShortArray): ShortArray {
 *         if (!enabled) return samples
 *
 *         // Process the audio samples
 *         val result = ShortArray(samples.size)
 *         for (i in samples.indices) {
 *             result[i] = // Apply your effect here
 *         }
 *         return result
 *     }
 *
 *     override fun isEnabled(): Boolean = enabled
 *
 *     override fun setEnabled(enabled: Boolean) {
 *         this.enabled = enabled
 *     }
 * }
 * ```
 */
interface Effect {
    /**
     * Unique identifier for the effect.
     * This ID is used to identify and manage the effect.
     */
    val id: String

    /**
     * Display name for the effect.
     * This is a human-readable name for UI presentation.
     */
    val name: String

    /**
     * Process audio samples through this effect.
     * This is where the actual audio processing happens.
     *
     * @param samples The audio samples to process (16-bit PCM data)
     * @return The processed audio samples
     */
    fun process(samples: ShortArray): ShortArray

    /**
     * Check if the effect is currently enabled.
     *
     * @return true if the effect is enabled, false otherwise
     */
    fun isEnabled(): Boolean

    /**
     * Enable or disable the effect.
     * Disabled effects should pass through audio unchanged.
     *
     * @param enabled true to enable the effect, false to disable
     */
    fun setEnabled(enabled: Boolean)
}