package org.operatorfoundation.audiowave.effects

import org.operatorfoundation.audiowave.Effect
import org.operatorfoundation.audiowave.utils.AudioUtils
import org.operatorfoundation.audiowave.utils.ParameterValidator
import timber.log.Timber

/**
 * Gain effect for adjusting audio volume.
 *
 * This effect applies a gain multiplier to audio samples, effectively controlling
 * the volume of the audio signal.
 *
 * Example Usage:
 * ```
 * val gainEffect = GainEffect()
 * gainEffect.setGain(1.5f) // Increase the volume by 50%
 * ```
 */
class GainEffect(override val id: String = "gain", override val name: String = "Gain") : Effect
{
    private var enabled = true
    private var gainValue = 1.0f

    /**
     * Set the gain value (volume multiplier).
     *
     * @param gain Gain value (1.0 = original volume, < 1.0 = quieter, > 1.0 = louder)
     * @return This effect instance for method chaining
     */
    fun set(gain: Float): GainEffect
    {
        gainValue = ParameterValidator.validateRange(gain, 0.0f, 5.0f, "Gain")
        Timber.d("Gain set to $gainValue")
        return this
    }

    override fun process(samples: ShortArray): ShortArray
    {
        if (!enabled || gainValue == 1.0f) { return samples }

        val result = ShortArray(samples.size)

        for (i in samples.indices)
        {
            // Apply gain with clipping prevention using our utility
            val sample = samples[i] * gainValue
            result[i] = AudioUtils.clipToShort(sample)
        }

        return result
    }

    override fun isEnabled(): Boolean = enabled

    /**
     * Enable or disable this effect.
     *
     * @param enabled True to enable, false to disable
     * @return This effect instance for method chaining
     */
    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        Timber.d("Gain effect ${if (enabled) "enabled" else "disabled"}")
    }
}