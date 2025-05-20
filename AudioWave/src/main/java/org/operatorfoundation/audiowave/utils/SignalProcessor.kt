package org.operatorfoundation.audiowave.utils

import timber.log.Timber

/**
 * Utility class for performing digital signal processing operations on audio data.
 *
 * This class provides common DSP algorithms used in audio signal processing,
 * including filtering operations and frequency analysis via Fast Fourier Transform (FFT).
 * It's designed to work with 16-bit PCM audio data represented as ShortArray values.
 *
 * Key features:
 * - Low-pass and high-pass filtering
 * - Fast Fourier Transform (FFT) implementation for spectral analysis
 * - Complex number handling for signal processing operations
 *
 * Example usage:
 * ```
 * // Create a signal processor
 * val processor = SignalProcessor()
 *
 * // Apply a low-pass filter at 1000 Hz to audio sampled at 44100 Hz
 * val filteredSamples = processor.applyLowPassFilter(samples, 1000f, 44100)
 *
 * // Perform frequency analysis
 * val spectrum = processor.fft(samples)
 * for (i in spectrum.indices) {
 *      val magnitude = spectrum[i].abs()
 *      // Process or display magnitude...
 * }
 * ```
 *
 * Note: This is a simplified implementation focused on readability and correctness.
 * For production audio applications requiring maximum performance, consider
 * using more optimized DSP libraries.
 */
class SignalProcessor
{
    /**
     * Applies a low-pass filter to the audio samples.
     *
     * A low-pass filter attenuates frequencies above the cutoff frequency while allowing
     * lower frequencies to pass through. This implementation uses a simple
     * single-pole Infinite Impulse Response (IIR) filter, also known as an RC filter.
     *
     * The cutoff frequency is the frequency at which the filter attenuates the input signal
     * by 3 dB (about 70.7% of the original amplitude).
     *
     * @param samples The audio samples to filter (16-bit PCM)
     * @param cutoffFrequency The cutoff frequency in Hz above which signals are attenuated
     * @param sampleRate The sample rate of the audio data in Hz
     * @return The filtered audio samples
     */
    fun applyLowPassFilter(samples: ShortArray, cutoffFrequency: Float, sampleRate: Int): ShortArray
    {
        try {
            if (samples.isEmpty()) return ShortArray(0)

            // Validate parameters
            val validatedFrequency = ParameterValidator.validatePositive(cutoffFrequency, "cutoffFrequency")
            val validatedSampleRate = ParameterValidator.validatePositive(sampleRate.toFloat(), "sampleRate").toInt()

            // RC time constant calculation (RC = 1/(2π*cutoffFreq))
            val rc = 1.0f / (2.0f * Math.PI.toFloat() * validatedFrequency)

            // Time between samples
            val dt = 1.0f / validatedSampleRate

            // Filter coefficient alpha = dt/(RC+dt)
            val alpha = dt / (rc + dt)

            val result = ShortArray(samples.size)

            // Initialize with first sample
            var prevFiltered = samples[0].toFloat()

            // Apply the filter: y[n] = α*x[n] + (1-α)*y[n-1]
            // This is the standard formula for a first-order low-pass filter
            for (i in samples.indices) {
                // Explicitly convert sample to float for consistent calculation
                val currentSample = samples[i].toFloat()

                // Calculate filtered sample using low-pass filter formula
                val filteredSample = alpha * currentSample + (1.0f - alpha) * prevFiltered

                // Convert back to short with proper range checking using our utility
                result[i] = AudioUtils.clipToShort(filteredSample)

                // Update state variable for next iteration
                prevFiltered = filteredSample
            }

            return result
        }
        catch (e: Exception)
        {
            Timber.e(e, "Error in low-pass filter")
            return samples  // Return original samples on error
        }
    }

    /**
     * Applies a high-pass filter to the audio samples.
     *
     * A high-pass filter attenuates frequencies below the cutoff frequency while
     * allowing higher frequencies to pass through. This implementation uses a simple
     * single-pole Infinite Impulse Response (IIR) filter.
     *
     * The cutoff frequency is the frequency at which the filter attenuates the
     * input signal by 3 dB (about 70.7% of the original amplitude).
     *
     * @param samples The audio samples to filter (16-bit PCM)
     * @param cutoffFrequency The cutoff frequency in Hz below which signals are attenuated
     * @param sampleRate The sample rate of the audio data in Hz
     * @return The filtered audio samples
     */
    fun applyHighPassFilter(samples: ShortArray, cutoffFrequency: Float, sampleRate: Int): ShortArray
    {
        if (samples.isEmpty()) return ShortArray(0)

        // RC time constant calculation (RC = 1/(2π*cutoffFreq))
        val rc = rc(cutoffFrequency)

        // Time between samples
        val dt = 1.0f / sampleRate

        // Filter coefficient alpha = RC/(RC+dt)
        val alpha = rc / (rc + dt)

        val result = ShortArray(samples.size)


        // Initialize with appropriate starting values
        // For first sample, assume previous sample and filtered value were zero
        var prevSample = 0.0f
        var prevFiltered = 0.0f

        /**
         * High-pass filter: Calculates the difference between the current and previous samples, then applies a weighted average.
         * This highlights changes (high frequencies) while attenuating constant signals (low frequencies).
         */

        // Apply the filter, a standard formula for a first-order high-pass filter:
        // y[n] = a*(y[n-1] + x[n] -x[n-1])
        for (i in samples.indices)
        {
            val currentSample = samples[i].toFloat()

            val filteredSample = alpha * (prevFiltered + currentSample - prevSample)

            // Convert back to short with range checking
            result[i] = when {
                filteredSample > Short.MAX_VALUE -> Short.MAX_VALUE
                filteredSample < Short.MIN_VALUE -> Short.MIN_VALUE
                else -> filteredSample.toInt().toShort()
            }

            prevSample = currentSample
            prevFiltered = filteredSample
        }

        return result
    }

    /**
     * Applies Fast Fourier Transform to analyze frequency content of the audio.
     *
     * The FFT converts time-domain audio samples into frequency-domain representation,
     * allowing analysis of the frequency components present in the signal.
     *
     * Note: For accurate frequency analysis, the number of samples should ideally be
     * a power of 2 (e.g., 512, 1024, 2048, etc.). Otherwise, the signal should be
     * zero-padded to the next power of 2.
     *
     * @param samples The audio samples to analyze (16-bit PCM)
     * @return An array of Complex numbers representing the frequency spectrum
     */
    fun fft(samples: ShortArray): Array<Complex> {
        // Create Complex input array from audio samples
        val n = samples.size
        val input = Array(n) { Complex(samples[it].toDouble(), 0.0) }

        return fft(input)
    }

    /**
     * Fast Fourier Transform implementation for complex numbers.
     *
     * This is a recursive implementation of the Cooley-Tukey FFT algorithm.
     * The algorithm has O(N log N) complexity, making it much faster than the
     * direct DFT calculation which has O(N²) complexity.
     *
     * @param x Array of Complex numbers to transform
     * @return Frequency domain representation as array of Complex numbers
     * @throws IllegalArgumentException if the input length is not a power of 2
     */
    fun fft(x: Array<Complex>): Array<Complex>
    {
        val n = x.size

        // Base case: single point
        if (n == 1) return x

        // Ensure input length is a power of 2
        if (n % 2 != 0) { throw IllegalArgumentException("FFT input length must be a power of 2") }

        // Split even and odd indices
        val even = Array(n / 2) { Complex(0.0, 0.0) }
        val odd = Array(n / 2) { Complex(0.0, 0.0) }

        for (k in 0 until n / 2)
        {
            even[k] = x[2 * k]
            odd[k] = x[2 * k + 1]
        }

        // Recursive calls for divide and conquer
        val evenFFT = fft(even)
        val oddFFT = fft(odd)

        // Combine the results with twiddle factors
        val result = Array(n) { Complex(0.0, 0.0) }

        for (k in 0 until n / 2)
        {
            // Twiddle factor W_N^k = e^(-2πik/N)
            val kth = -2 * k * Math.PI / n
            val wk = Complex(Math.cos(kth), Math.sin(kth))
            val oddK = wk.multiply(oddFFT[k])

            // Butterfly operations to combine even and odd results
            result[k] = evenFFT[k].add(oddK)
            result[k + n / 2] = evenFFT[k].subtract(oddK)
        }

        return result
    }

    /**
     * Calculates the Root Mean Square (RMS) level of audio samples.
     *
     * RMS is a good measure of audio level as it accounts for both positive
     * and negative sample values and correlates well with perceived loudness.
     *
     * @param samples The audio samples to analyze (16-bit PCM)
     * @return RMS level normalized to range 0.0 - 1.0
     */
    fun calculateRmsLevel(samples: ShortArray): Float
    {
        if (samples.isEmpty()) return 0.0f

        var sumOfSquares = 0.0
        for (sample in samples)
        {
            sumOfSquares += sample * sample
        }

        val meanSquare = sumOfSquares / samples.size
        val rms = Math.sqrt(meanSquare)

        // Normalize to 0.0-1.0 range (16-bit audio has a range of -32768 to 32767)
        return (rms / 32768.0).toFloat()
    }

    /**
     * Calculates the RMS level from raw PCM byte data.
     *
     * This method converts raw byte data (typically from USB audio input)
     * into 16-bit samples and then calculates the RMS level.
     *
     * @param data Raw PCM byte data (assumed to be 16-bit, little endien)
     * @return RMS level normalized to a range of 0.0 - 1.0
     */
    fun calculateRmsLevelFromBytes(data: ByteArray): Float
    {
        return AudioUtils.calculateRmsLevel(data)
    }

    fun rc(cutoffFrequency: Float): Float
    {
        // RC time constant calculation (RC = 1/(2π*cutoffFreq))
        return 1.0f / (2.0f * Math.PI.toFloat() * cutoffFrequency)
    }


    /**
     * Complex number class for FFT and other signal processing operations.
     *
     * This nested class provides basic complex number arithmetic operations
     * required for the FFT algorithm.
     *
     * @property real The real part of the complex number
     * @property imag The imaginary part of the complex number
     */
    data class Complex(val real: Double, val imag: Double)
    {
        /**
         * Add another complex number to this one.
         *
         * @param other The other complex number to add
         * @return The sum as a new Complex number
         */
        fun add(other: Complex): Complex
        {
            return Complex(real + other.real, imag + other.imag)
        }

        /**
         * Subtract another complex number from this one.
         *
         * @param other The complex number to subtract
         * @return The difference as a new Complex number
         */
        fun subtract(other: Complex): Complex
        {
            return Complex(real - other.real, imag - other.imag)
        }

        /**
         * Multiply this complex number by another.
         *
         * @param other The complex number to multiply by
         * @return The product as a new Complex number
         */
        fun multiply(other: Complex): Complex {
            return Complex(
                real * other.real - imag * other.imag,
                real * other.imag + imag * other.real
            )
        }

        /**
         * Calculate the magnitude (absolute value) of this complex number.
         *
         * @return The magnitude as a double
         */
        fun abs(): Double
        {
            return Math.sqrt(real * real + imag * imag)
        }

        /**
         * Calculate the phase angle of this complex number in radians.
         *
         * @return The phase angle in radians
         */
        fun phase(): Double
        {
            return Math.atan2(imag, real)
        }

        override fun toString(): String {
            return when {
                imag == 0.0 -> "$real"
                real == 0.0 -> "${imag}i"
                imag < 0 -> "$real - ${imag}i"
                else -> "$real + ${imag}i"
            }
        }
    }

}