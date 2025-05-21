package org.operatorfoundation.audiowave.decoder

import org.operatorfoundation.audiowave.utils.AudioUtils
import org.operatorfoundation.audiowave.utils.ParameterValidator
import org.operatorfoundation.audiowave.utils.SignalProcessor
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for standard radio wave signals (AM/FM).
 *
 * This decoder can process audio input to extract AM or FM radio signals.
 * It uses digital signal processing techniques to demodulate the signals
 * and extract the audio content.
 *
 * Example usage:
 * ```
 * // Configure for FM radio at 98.5 MHz
 * radioDecoder.configure(mapOf(
 *     "mode" to "fm",
 *     "centerFrequency" to 98.5f,
 *     "bandwidth" to 15.0f
 * ))
 *
 * // Decode audio data
 * val decodedFm = radioDecoder.decode(audioData)
 * ```
 */
class RadioWaveDecoder : AudioDecoder
{
    override val id: String = "radio_wave"
    override val name: String = "Radio Wave Decoder"
    override val description: String = "Decodes AM/FM radio signals from audio input"

    private val signalProcessor = SignalProcessor()
    private var isAmMode = true
    private var centerFrequency = 100.0f // Default FM frequency in MHz
    private var bandwidth = 15.0f // Default bandwidth in kHz
    private var sampleRate = 44100 // Sample rate in Hz

    /**
     * Configure the decoder with specific parameters.
     *
     * @param params Map of parameter names to parameter values:
     *  - "mode": String ("am" or "fm")
     *  - "centerFrequency": Float (frequency in MHz)
     *  - "bandwidth": Float (bandwidth in kHz)
     *  - "sampleRate": Int (sample rate in Hz)
     */
    override fun configure(params: Map<String, Any>) {
        params["mode"]?.let {
            isAmMode = when (it.toString().lowercase()) {
                "am" -> true
                "fm" -> false
                else -> {
                    Timber.w("Invalid mode: $it, defaulting to AM.")
                    true
                }
            }
            Timber.d("Radio mode set to ${if (isAmMode) "AM" else "FM"}")
        }

        params["centerFrequency"]?.let {
            centerFrequency = ParameterValidator.validatePositive((it as Number).toFloat(), "centerFrequency")
            Timber.d("Center frequency set to $centerFrequency MHz")
        }

        params["bandwidth"]?.let {
            bandwidth = ParameterValidator.validatePositive((it as Number).toFloat(), "bandwidth")
            Timber.d("Bandwidth set to $bandwidth kHz")
        }

        params["sampleRate"]?.let {
            sampleRate = ParameterValidator.validatePositive((it as Number).toFloat(), "sampleRate").toInt()
            Timber.d("Sampe rate set to $sampleRate Hz")
        }
    }

    /**
     * Decode the audio based on the current configuration.
     *
     * @param audioData Raw audio data to decode
     * @return Decoded audio data
     */
    override fun decode(audioData: ByteArray): ByteArray
    {
        // Convert byte array to short array for processing
        val samples = AudioUtils.bytesToShorts(audioData)

        // Process based on mode
        val decodedSamples = if (isAmMode)
        {
            decodeAm(samples)
        }
        else
        {
            decodeFm(samples)
        }

        // Convert back to byte array
        return AudioUtils.shortsToBytes(decodedSamples)
    }

    /**
     * Decode AM signal using envelope detection.
     *
     * @param samples Audio samples to decode
     * @return Decoded audio samples
     */
    private fun decodeAm(samples: ShortArray): ShortArray
    {
        // AM enveloper detector
        val result = ShortArray(samples.size)
        var prevSample = 0.0

        for (i in samples.indices)
        {
            // Take the absolute value for envelope detection
            val envelope = Math.abs(samples[i].toDouble())

            // Low-pass filter to smooth the envelope
            val alpha = 0.1 // Smoothing factor
            val smoothed = alpha * envelope + (1 - alpha) * prevSample
            prevSample = smoothed

            result[i] = AudioUtils.clipToShort(smoothed.toFloat())
        }

        // Apply bandpass filter centered on the target frequency
        return signalProcessor.applyLowPassFilter(result, bandwidth * 1000, sampleRate)
    }

    /**
     * Decode FM signal using FM demodulation.
     *
     * @param samples Audio samples to decode
     * @return Decoded audio samples
     */
    private fun decodeFm(samples: ShortArray): ShortArray
    {
        // FM demodulation by calculating phase differences
        val result = ShortArray(samples.size)
        var prevPhase = 0.0

        // First apply bandpass filter around center frequency
        val bandpassFilter = bandwidth * 1000 / 2
        val filteredSamples = signalProcessor.applyHighPassFilter(
            signalProcessor.applyLowPassFilter(samples, centerFrequency + bandpassFilter, sampleRate),
            centerFrequency - bandpassFilter,
            sampleRate)

        // Convert to complex signal (I/Q)
        val complex = Array(filteredSamples.size) {
            SignalProcessor.Complex(filteredSamples[it].toDouble(), 0.0)
        }

        // Perform Hilbert transform
        for (i in 1 until complex.size - 1) {
            complex[i] = SignalProcessor.Complex(
                complex[i].real,
                (complex[i+1].real - complex[i-1].real) / 2
            )
        }

        // FM Demodulation by finding instantaneous phase and differentiating
        for (i in 1 until complex.size)
        {
            val phase = Math.atan2(complex[i].imag, complex[i].real)
            var phaseDiff = phase - prevPhase

            // Wrap phase difference to -π to π
            if (phaseDiff > Math.PI) phaseDiff -= 2 * Math.PI
            if (phaseDiff < -Math.PI) phaseDiff += 2 * Math.PI

            prevPhase = phase

            // Scale the phase difference to get the demodulated signal
            result[i] = AudioUtils.clipToShort((phaseDiff * 1000).toFloat())
        }

        // Apply low-pass filter to get the baseband signal
        return signalProcessor.applyLowPassFilter(result, bandwidth * 1000, sampleRate)
    }
}