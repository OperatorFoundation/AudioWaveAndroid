package org.operatorfoundation.audiowave.effects

import org.operatorfoundation.audiowave.effects.Effect
import org.operatorfoundation.audiowave.utils.AudioUtils
import org.operatorfoundation.audiowave.utils.ParameterValidator
import timber.log.Timber

/**
 * Echo effect for adding delay and decay to audio.
 *
 * This effect adds a delayed and attenuated version of the audio signal
 * back into the original signal, creating an echo or reverb-like effect.
 *
 * Example usage:
 * ```
 * val echoEffect = EchoEffect()
 * echoEffect.setDelay(0.3f)  // 300ms delay
 *          .setDecay(0.6f)   // 60% decay
 * ```
 */
class EchoEffect(
    override val id: String = "echo",
    override val name: String = "Echo"
) : Effect
{
    private var enabled = false
    private var echoDelay = 0.3f  // Echo delay in seconds
    private var echoDecay = 0.5f  // Echo decay factor

    // Echo buffer
    private val echoBuffer = ArrayList<Short>()
    private var echoBufferSize = 44100 / 2  // Will be properly set when used

    /**
     * Set the echo delay in seconds.
     *
     * @param delay Delay time in seconds
     * @return This effect instance for method chaining
     */
    fun setDelay(delay: Float): EchoEffect
    {
        echoDelay = ParameterValidator.validateRange(delay, 0.1f, 2.0f, "Echo delay")
        Timber.d("Echo delay set to $echoDelay seconds")

        // Resize echo buffer
        val newSize = (44100 * echoDelay).toInt()

        if (newSize != echoBufferSize)
        {
            echoBufferSize = newSize
            echoBuffer.clear()
            for (i in 0 until echoBufferSize)
            {
                echoBuffer.add(0)
            }
            Timber.d("Echo buffer resized to $echoBufferSize samples")
        }
        return this
    }

    /**
     * Set the echo decay factor.
     *
     * @param decay Decay factor (0.0 = no echo, 1.0 = echo never decays)
     * @return This effect instance for method chaining
     */
    fun setDecay(decay: Float): EchoEffect
    {
        echoDecay = ParameterValidator.validateRange(decay, 0.0f, 0.95f, "Echo decay")
        Timber.d("Echo decay factor set to $echoDecay")
        return this
    }

    override fun process(samples: ShortArray): ShortArray
    {
        if (!enabled) { return samples }

        // Initialize echo buffer if needed
        if (echoBuffer.isEmpty())
        {
            echoBufferSize = (44100 * echoDelay).toInt()
            for (i in 0 until echoBufferSize)
            {
                echoBuffer.add(0)
            }
            Timber.d("Initialized echo buffer with size $echoBufferSize")
        }

        val result = ShortArray(samples.size)

        for (i in samples.indices)
         {
            // Get echo sample
            val echoSample = echoBuffer[i % echoBufferSize]

            // Mix original audio with echo
            val mixedSample = samples[i] + (echoSample * echoDecay).toInt()

            // Prevent clipping using our utility
            result[i] = AudioUtils.clipToShort(mixedSample.toFloat())

            // Store current sample in echo buffer for future echoes
            echoBuffer[i % echoBufferSize] = samples[i]
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
    override fun setEnabled(enabled: Boolean)
    {
        this.enabled = enabled

        // Initialize buffer if enabling
        if (enabled && echoBuffer.isEmpty())
        {
            echoBufferSize = (44100 * echoDelay).toInt()
            for (i in 0 until echoBufferSize)
            {
                echoBuffer.add(0)
            }
            Timber.d("Initialized echo buffer on enable with size $echoBufferSize")
        }

        Timber.d("Echo effect ${if (enabled) "enabled" else "disabled"}")
    }
}