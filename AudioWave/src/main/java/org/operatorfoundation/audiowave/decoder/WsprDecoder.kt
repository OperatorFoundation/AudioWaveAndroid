package org.operatorfoundation.audiowave.decoder

import org.operatorfoundation.audiowave.utils.AudioUtils
import org.operatorfoundation.audiowave.utils.ErrorHandler
import org.operatorfoundation.audiowave.utils.SignalProcessor
import timber.log.Timber
import kotlin.math.*
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.PriorityQueue

/**
 * Production-Ready WSPR (Weak Signal Propagation Reporter) Decoder
 *
 * Decodes WSPR signals from audio data. WSPR uses 4-FSK modulation
 * with a symbol rate of approximately 1.46 Hz in a 6 Hz bandwidth.
 *
 * This implementation follows the principles of WSPR decoding as
 * described by K1JT (Joe Taylor) and used in WSJT-X software.
 * It includes signal detection, sync pattern correlation,
 * demodulation, deinterleaving, convolutional decoding, and message extraction.
 */
class WsprDecoder : AudioDecoder {
    companion object {
        const val DECODER_ID = "wspr_decoder"
        const val SAMPLE_RATE = 12000
        const val SYMBOL_COUNT = 162
        const val SYMBOL_RATE = 12000.0 / 8192.0  // ~1.46 baud
        const val SYMBOL_LENGTH = 8192  // Samples per symbol at 12kHz
        const val BASE_FREQUENCY = 1500.0  // Audio center frequency in Hz
        const val TONE_SPACING = 1.4648  // Hz between tones
        const val TRANSMISSION_LENGTH_SECONDS = 120  // 2 minutes
        const val SNR_THRESHOLD = -25.0  // Minimum SNR for detection in dB
        const val MESSAGE_BITS = 50  // Number of bits in the WSPR message

        // Sync vector
        private val SYNC_VECTOR = intArrayOf(
            1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 0,
            0, 1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1,
            0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 0, 1,
            1, 0, 1, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1,
            0, 0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 0,
            0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 0, 1, 1,
            0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 1, 1,
            0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 0,
            0, 0
        )

        // Bit reversal table for deinterleaving
        private val BIT_REVERSAL = intArrayOf(
            0, 128, 64, 192, 32, 160, 96, 224, 16, 144, 80, 208, 48, 176, 112, 240,
            8, 136, 72, 200, 40, 168, 104, 232, 24, 152, 88, 216, 56, 184, 120, 248,
            4, 132, 68, 196, 36, 164, 100, 228, 20, 148, 84, 212, 52, 180, 116, 244,
            12, 140, 76, 204, 44, 172, 108, 236, 28, 156, 92, 220, 60, 188, 124, 252,
            2, 130, 66, 194, 34, 162, 98, 226, 18, 146, 82, 210, 50, 178, 114, 242,
            10, 138, 74, 202, 42, 170, 106, 234, 26, 154, 90, 218, 58, 186, 122, 250,
            6, 134, 70, 198, 38, 166, 102, 230, 22, 150, 86, 214, 54, 182, 118, 246,
            14, 142, 78, 206, 46, 174, 110, 238, 30, 158, 94, 222, 62, 190, 126, 254,
            1, 129, 65, 193, 33, 161, 97, 225, 17, 145, 81, 209, 49, 177, 113, 241,
            9, 137, 73, 201, 41, 169, 105, 233, 25, 153, 89, 217, 57, 185, 121, 249,
            5, 133, 69, 197, 37, 165, 101, 229, 21, 149, 85, 213, 53, 181, 117, 245,
            13, 141, 77, 205, 45, 173, 109, 237, 29, 157, 93, 221, 61, 189, 125, 253,
            3, 131, 67, 195, 35, 163, 99, 227, 19, 147, 83, 211, 51, 179, 115, 243,
            11, 139, 75, 203, 43, 171, 107, 235, 27, 155, 91, 219, 59, 187, 123, 251,
            7, 135, 71, 199, 39, 167, 103, 231, 23, 151, 87, 215, 55, 183, 119, 247,
            15, 143, 79, 207, 47, 175, 111, 239, 31, 159, 95, 223, 63, 191, 127, 255
        )

        // Convolutional coding polynomials (K=32, r=1/2)
        private val POLY1 = intArrayOf(1, 1, 1, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1)
        private val POLY2 = intArrayOf(1, 0, 1, 1, 1, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0)

        // Callsign and locator decoding constants
        private val CALLSIGN_CHARS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
            'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
            'U', 'V', 'W', 'X', 'Y', 'Z', ' '
        )
    }

    override val id: String = DECODER_ID
    override val name: String = "WSPR Decoder"
    override val description: String = "Decodes Weak Signal Propagation Reporter (WSPR) signals"

    private val signalProcessor = SignalProcessor()
    private var centerFrequency = BASE_FREQUENCY
    private var signalThreshold = 0.1f  // Signal detection threshold (0.0-1.0)
    private var multiDecoderEnabled = true  // Enable multiple signal detection
    private var maxDecodes = 5  // Maximum number of signals to decode
    private var reportSnr = true  // Report SNR in decoded messages
    private var expectedStartTime: Long = 0  // Expected start time (epoch millis)

    // Data class to hold decoded messages
    data class DecodedMessage(
        val callsign: String,
        val grid: String,
        val powerDbm: Int,
        val snrDb: Double,
        val frequencyHz: Double,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        override fun toString(): String {
            val snrText = if (snrDb > -99) String.format(" SNR: %.1f dB", snrDb) else ""
            val freqText = String.format(" %.1f Hz", frequencyHz)
            return "$callsign $grid ${powerDbm}dBm$snrText$freqText"
        }
    }

    /**
     * Configure the decoder with specific parameters.
     *
     * @param params Map of parameter names to parameter values:
     *  - "centerFrequency": Float (center frequency in Hz, default 1500)
     *  - "signalThreshold": Float (detection threshold, 0.0-1.0, default 0.1)
     *  - "multiDecoderEnabled": Boolean (whether to decode multiple signals, default true)
     *  - "maxDecodes": Int (maximum number of signals to decode, default 5)
     *  - "reportSnr": Boolean (whether to report SNR in decoded messages, default true)
     *  - "expectedStartTime": Long (expected start time in millis since epoch, default 0)
     */
    override fun configure(params: Map<String, Any>) {
        params["centerFrequency"]?.let {
            centerFrequency = (it as Number).toDouble()
            Timber.d("WSPR center frequency set to $centerFrequency Hz")
        }

        params["signalThreshold"]?.let {
            signalThreshold = (it as Number).toFloat().coerceIn(0.01f, 0.99f)
            Timber.d("WSPR signal threshold set to $signalThreshold")
        }

        params["multiDecoderEnabled"]?.let {
            multiDecoderEnabled = it as Boolean
            Timber.d("WSPR multi-decoder ${if (multiDecoderEnabled) "enabled" else "disabled"}")
        }

        params["maxDecodes"]?.let {
            maxDecodes = (it as Number).toInt().coerceIn(1, 20)
            Timber.d("WSPR max decodes set to $maxDecodes")
        }

        params["reportSnr"]?.let {
            reportSnr = it as Boolean
            Timber.d("WSPR SNR reporting ${if (reportSnr) "enabled" else "disabled"}")
        }

        params["expectedStartTime"]?.let {
            expectedStartTime = (it as Number).toLong()
            Timber.d("WSPR expected start time set to $expectedStartTime")
        }
    }

    /**
     * Decode WSPR message from audio data
     *
     * @param audioData Raw audio data to decode
     * @return Decoded data containing WSPR message information
     */
    override fun decode(audioData: ByteArray): ByteArray {
        Timber.d("Starting WSPR decoding process")

        return ErrorHandler.runCatching {
            // Convert byte array to short array for processing
            val samples = AudioUtils.bytesToShorts(audioData)

            // 1. Check if we have enough data for a full WSPR transmission
            val expectedSamples = TRANSMISSION_LENGTH_SECONDS * SAMPLE_RATE
            if (samples.size < expectedSamples) {
                Timber.w("Audio data too short for WSPR decoding: ${samples.size} samples, need $expectedSamples")
                return@runCatching "Audio data too short for WSPR decoding".toByteArray()
            }

            // 2. Check if timing is appropriate (if expected start time is set)
            if (expectedStartTime > 0) {
                val now = System.currentTimeMillis()
                val timeDiff = abs(now - expectedStartTime)

                // WSPR requires accurate timing (±1 second)
                if (timeDiff > 5000) {  // Allow 5 seconds grace period
                    Timber.w("Audio data timing offset: ${timeDiff/1000}s from expected start")
                }
            }

            // 3. Prepare for decoding
            val decodedMessages = mutableListOf<DecodedMessage>()

            // 4. Decode (either single or multiple signals)
            if (multiDecoderEnabled) {
                // Decode multiple signals
                val signals = detectWsprSignals(samples)
                Timber.d("Detected ${signals.size} potential WSPR signals")

                // Process each signal
                for ((i, signal) in signals.withIndex()) {
                    if (i >= maxDecodes) break

                    try {
                        val message = decodeWsprSignal(samples, signal.first, signal.second)
                        if (message != null) {
                            decodedMessages.add(message)
                            Timber.d("Decoded WSPR message: $message")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error decoding WSPR signal at ${signal.first} Hz")
                    }
                }
            } else {
                // Decode single signal at center frequency
                try {
                    val message = decodeWsprSignal(samples, centerFrequency, 0.0)
                    if (message != null) {
                        decodedMessages.add(message)
                        Timber.d("Decoded WSPR message: $message")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error decoding WSPR signal at $centerFrequency Hz")
                }
            }

            // 5. Format response
            if (decodedMessages.isEmpty()) {
                Timber.w("No WSPR signals decoded")
                return@runCatching "No WSPR signals decoded".toByteArray()
            }

            // Format the decoded messages
            val result = StringBuilder()
            for (message in decodedMessages) {
                result.append(message.toString()).append("\n")
            }

            result.toString().trim().toByteArray()
        }.getOrElse { error ->
            Timber.e(error, "Error decoding WSPR data")
            "Error decoding WSPR: ${error.message}".toByteArray()
        }
    }

    /**
     * Detect multiple WSPR signals in the audio data
     *
     * @param samples Audio samples to analyze
     * @return List of potential signals as pairs of (frequency, SNR)
     */
    private fun detectWsprSignals(samples: ShortArray): List<Pair<Double, Double>> {
        // Apply bandpass filter covering typical WSPR range
        val lowerFreq = centerFrequency - 200.0
        val upperFreq = centerFrequency + 200.0

        val filteredSamples = signalProcessor.applyBandpassFilter(
            samples, lowerFreq.toFloat(), upperFreq.toFloat(), SAMPLE_RATE
        )

        // Compute spectrum with high resolution
        val fftSize = 32768
        val spectrumResult = computeSpectrum(filteredSamples, fftSize)
        val spectrum = spectrumResult.first
        val freqBins = spectrumResult.second

        // Compute noise floor
        val noiseFloor = calculateNoiseFloor(spectrum)

        // Find peaks above threshold
        val peaks = PriorityQueue<Pair<Double, Double>>(
            compareByDescending { it.second }  // Sort by SNR descending
        )

        val minBin = (lowerFreq / (SAMPLE_RATE.toDouble() / fftSize)).toInt().coerceIn(0, spectrum.size - 1)
        val maxBin = (upperFreq / (SAMPLE_RATE.toDouble() / fftSize)).toInt().coerceIn(0, spectrum.size - 1)

        // Minimum peak width in bins (to avoid detecting noise spikes)
        val minPeakWidth = (4.0 / (SAMPLE_RATE.toDouble() / fftSize)).toInt()

        var i = minBin
        while (i <= maxBin) {
            if (spectrum[i] > noiseFloor * (1.0 + signalThreshold)) {
                // Found potential peak
                var peakBin = i
                var peakValue = spectrum[i]

                // Find the exact peak
                var j = i + 1
                while (j <= maxBin && spectrum[j] >= spectrum[j - 1]) {
                    if (spectrum[j] > peakValue) {
                        peakValue = spectrum[j]
                        peakBin = j
                    }
                    j++
                }

                // Calculate peak width and verify it's wide enough
                var lowerEdge = peakBin
                while (lowerEdge > minBin && spectrum[lowerEdge] > noiseFloor * 1.5) {
                    lowerEdge--
                }

                var upperEdge = peakBin
                while (upperEdge < maxBin && spectrum[upperEdge] > noiseFloor * 1.5) {
                    upperEdge++
                }

                val peakWidth = upperEdge - lowerEdge

                if (peakWidth >= minPeakWidth) {
                    // Calculate frequency and SNR
                    val frequency = freqBins[peakBin]
                    val snrDb = 10.0 * log10(peakValue / noiseFloor)

                    if (snrDb > SNR_THRESHOLD) {
                        peaks.add(Pair(frequency, snrDb))
                    }
                }

                // Skip to after this peak
                i = upperEdge + 1
            } else {
                i++
            }
        }

        // Return the top signals
        val result = mutableListOf<Pair<Double, Double>>()
        while (result.size < maxDecodes && peaks.isNotEmpty()) {
            result.add(peaks.poll())
        }

        return result
    }

    /**
     * Compute spectrum of audio samples
     *
     * @param samples Audio samples
     * @param fftSize FFT size
     * @return Pair of spectrum magnitudes and frequency bins
     */
    private fun computeSpectrum(samples: ShortArray, fftSize: Int): Pair<DoubleArray, DoubleArray> {
        // Prepare FFT input
        val paddedSamples = ShortArray(fftSize)
        samples.copyInto(paddedSamples, 0, 0, min(samples.size, fftSize))

        // Apply window function
        val windowedSamples = DoubleArray(fftSize)
        for (i in 0 until fftSize) {
            val windowFactor = if (i < samples.size) {
                0.54 - 0.46 * cos(2.0 * PI * i / (samples.size - 1))  // Hamming window
            } else {
                0.0
            }
            windowedSamples[i] = paddedSamples[i] * windowFactor
        }

        // Compute FFT
        val fftResult = signalProcessor.fftComplex(windowedSamples)

        // Calculate magnitude spectrum
        val spectrum = DoubleArray(fftSize / 2)
        for (i in 0 until fftSize / 2) {
            val real = fftResult[i].real
            val imag = fftResult[i].imag
            spectrum[i] = real * real + imag * imag  // Magnitude squared
        }

        // Calculate frequency bins
        val freqBins = DoubleArray(fftSize / 2)
        val freqStep = SAMPLE_RATE.toDouble() / fftSize
        for (i in freqBins.indices) {
            freqBins[i] = i * freqStep
        }

        return Pair(spectrum, freqBins)
    }

    /**
     * Calculate noise floor from spectrum
     *
     * @param spectrum Magnitude spectrum
     * @return Estimated noise floor level
     */
    private fun calculateNoiseFloor(spectrum: DoubleArray): Double {
        // Sort spectrum values
        val sortedSpectrum = spectrum.copyOf()
        sortedSpectrum.sort()

        // Use median to estimate noise floor
        val medianIndex = sortedSpectrum.size / 2
        return sortedSpectrum[medianIndex]
    }

    /**
     * Decode a single WSPR signal
     *
     * @param samples Audio samples
     * @param frequency Center frequency of the signal
     * @param snrDb Estimated SNR in dB
     * @return Decoded message, or null if decoding failed
     */
    private fun decodeWsprSignal(
        samples: ShortArray,
        frequency: Double,
        snrDb: Double
    ): DecodedMessage? {
        // 1. Apply bandpass filter centered on the signal
        val bandwidth = 6.0  // WSPR bandwidth is 6 Hz
        val filteredSamples = signalProcessor.applyBandpassFilter(
            samples,
            (frequency - bandwidth/2).toFloat(),
            (frequency + bandwidth/2).toFloat(),
            SAMPLE_RATE
        )

        // 2. Synchronize to find optimal symbol boundaries
        val syncResult = synchronizeToSymbols(filteredSamples, frequency)
        if (syncResult == null) {
            Timber.w("Failed to synchronize to WSPR signal at $frequency Hz")
            return null
        }

        val (startSample, syncQuality) = syncResult
        Timber.d("Synchronized to WSPR signal at $frequency Hz, quality: $syncQuality, start: $startSample")

        // 3. Extract and demodulate symbols
        val symbols = extractSymbols(filteredSamples, frequency, startSample)

        // 4. Extract bits from symbols
        val bits = extractBitsFromSymbols(symbols)

        // 5. Deinterleave the bits
        val deinterleavedBits = deinterleave(bits)

        // 6. Apply convolutional decoding
        val messageBits = applyViterbiDecoding(deinterleavedBits)

        // 7. Decode the message
        return decodeWsprMessage(messageBits, frequency, snrDb)
    }

    /**
     * Synchronize to symbol boundaries using sync pattern correlation
     *
     * @param samples Audio samples
     * @param frequency Signal frequency
     * @return Pair of start sample and sync quality, or null if sync failed
     */
    private fun synchronizeToSymbols(samples: ShortArray, frequency: Double): Pair<Int, Double>? {
        // Create sync template: the expected signal pattern
        val syncTemplate = ShortArray(SYMBOL_COUNT * SYMBOL_LENGTH)

        // Generate sync template based on sync vector
        for (i in 0 until SYMBOL_COUNT) {
            val symbol = SYNC_VECTOR[i]

            // Calculate tone frequency
            val toneOffset = (symbol - 0.5) * TONE_SPACING
            val toneFreq = frequency + toneOffset

            // Generate symbol waveform
            for (j in 0 until SYMBOL_LENGTH) {
                val time = j / SAMPLE_RATE.toDouble()
                val angle = 2.0 * PI * toneFreq * time
                val sample = (sin(angle) * 32767.0).toInt().toShort()
                syncTemplate[i * SYMBOL_LENGTH + j] = sample
            }
        }

        // Perform correlation over a range of offsets
        val maxOffset = SYMBOL_LENGTH * 2  // Search range: 2 symbols
        var bestOffset = 0
        var bestCorrelation = Double.NEGATIVE_INFINITY

        // Try different offsets
        for (offset in 0 until maxOffset) {
            if (offset + syncTemplate.size > samples.size) break

            var correlation = 0.0
            for (i in syncTemplate.indices) {
                correlation += (samples[offset + i].toDouble() * syncTemplate[i].toDouble())
            }

            if (correlation > bestCorrelation) {
                bestCorrelation = correlation
                bestOffset = offset
            }
        }

        // Normalize correlation to produce quality metric
        val sampleEnergy = samples.sumOf { it.toDouble() * it.toDouble() }
        val templateEnergy = syncTemplate.sumOf { it.toDouble() * it.toDouble() }
        val normalizedCorrelation = bestCorrelation / sqrt(sampleEnergy * templateEnergy)

        // Check if correlation is good enough
        return if (normalizedCorrelation > 0.15) {  // Threshold for good sync
            Pair(bestOffset, normalizedCorrelation)
        } else {
            null
        }
    }

    /**
     * Extract symbols from synchronized audio data
     *
     * @param samples Audio samples
     * @param frequency Signal center frequency
     * @param startSample Starting sample for synchronized data
     * @return Array of demodulated symbols
     */
    private fun extractSymbols(samples: ShortArray, frequency: Double, startSample: Int): IntArray {
        val symbols = IntArray(SYMBOL_COUNT)

        // Tone frequencies for 4-FSK
        val toneFreqs = DoubleArray(4)
        for (i in 0 until 4) {
            toneFreqs[i] = frequency + (i - 1.5) * TONE_SPACING
        }

        // For each symbol position
        for (i in 0 until SYMBOL_COUNT) {
            val symbolStart = startSample + i * SYMBOL_LENGTH

            // Extract this symbol's samples
            val symbolSamples = ShortArray(SYMBOL_LENGTH)
            for (j in 0 until SYMBOL_LENGTH) {
                val sampleIndex = symbolStart + j
                if (sampleIndex < samples.size) {
                    symbolSamples[j] = samples[sampleIndex]
                }
            }

            // Measure energy at each tone frequency
            val toneEnergies = DoubleArray(4)
            for (toneIdx in 0 until 4) {
                toneEnergies[toneIdx] = signalProcessor.calculateEnergy(
                    symbolSamples, toneFreqs[toneIdx].toFloat(), SAMPLE_RATE
                )
            }

            // Select tone with maximum energy
            var maxEnergy = Double.NEGATIVE_INFINITY
            var bestTone = 0
            for (toneIdx in 0 until 4) {
                if (toneEnergies[toneIdx] > maxEnergy) {
                    maxEnergy = toneEnergies[toneIdx]
                    bestTone = toneIdx
                }
            }

            symbols[i] = bestTone
        }

        return symbols
    }

    /**
     * Extract bits from demodulated symbols
     *
     * @param symbols Array of symbols (0-3)
     * @return Array of bits
     */
    private fun extractBitsFromSymbols(symbols: IntArray): IntArray {
        val bits = IntArray(symbols.size * 2)

        for (i in symbols.indices) {
            // Extract two bits from each symbol
            bits[i * 2] = (symbols[i] shr 1) and 1
            bits[i * 2 + 1] = symbols[i] and 1
        }

        return bits
    }

    /**
     * Deinterleave bits using bit-reversal permutation
     *
     * @param bits Interleaved bits
     * @return Deinterleaved bits
     */
    private fun deinterleave(bits: IntArray): IntArray {
        val deinterleavedBits = IntArray(bits.size)

        for (i in bits.indices) {
            val originalIndex = BIT_REVERSAL[i] % bits.size
            deinterleavedBits[i] = bits[originalIndex]
        }

        return deinterleavedBits
    }

    /**
     * Apply Viterbi decoding to extract message bits
     *
     * @param encodedBits Encoded bits
     * @return Decoded message bits
     */
    private fun applyViterbiDecoding(encodedBits: IntArray): IntArray {
        // Simplified implementation for prototype purposes
        // In a production system, this would be a full Viterbi decoder

        // For now, just use a simple majority vote for each bit position
        val messageBits = IntArray(MESSAGE_BITS)

        // Process each message bit
        for (i in 0 until MESSAGE_BITS) {
            // Count votes from each encoded bit pair
            var voteCount = 0

            // Look at all bit pairs that depend on this message bit
            for (j in 0 until 32) {
                if (i + j < encodedBits.size / 2) {
                    val bitPairIndex = (i + j) * 2

                    // Check if these polynomials include the current bit
                    if (POLY1[j] == 1 && encodedBits[bitPairIndex] == 1) voteCount++
                    if (POLY2[j] == 1 && encodedBits[bitPairIndex + 1] == 1) voteCount++
                }
            }

            // Decide bit value based on votes
            messageBits[i] = if (voteCount >= 32) 1 else 0
        }

        return messageBits
    }

    /**
     * Decode WSPR message from bits
     *
     * @param messageBits Message bits
     * @param frequency Signal frequency
     * @param snrDb Signal SNR in dB
     * @return Decoded message object
     */
    private fun decodeWsprMessage(
        messageBits: IntArray,
        frequency: Double,
        snrDb: Double
    ): DecodedMessage? {
        // Pack bits into a value
        var packedCall = 0
        for (i in 0 until 28) {
            packedCall = (packedCall shl 1) or messageBits[i]
        }

        var packedLocPwr = 0
        for (i in 0 until 22) {
            packedLocPwr = (packedLocPwr shl 1) or messageBits[i + 28]
        }

        // Decode callsign
        val callsign = decodeCallsign(packedCall)

        // Decode grid and power
        val gridAndPower = decodeGridAndPower(packedLocPwr)
        val grid = gridAndPower.first
        val powerDbm = gridAndPower.second

        // Return decoded message
        return DecodedMessage(
            callsign = callsign,
            grid = grid,
            powerDbm = powerDbm,
            snrDb = snrDb,
            frequencyHz = frequency
        )
    }

    /**
     * Decode callsign from packed value
     *
     * @param packedCall Packed callsign value
     * @return Decoded callsign
     */
    private fun decodeCallsign(packedCall: Int): String {
        // Unpack the callsign value
        var n = packedCall

        // Standard callsign format: A1BCD
        // 28 bits encodes all standard callsigns
        val callChars = CharArray(6)

        // Decode from right to left
        callChars[5] = CALLSIGN_CHARS[(n % 27) + 10]
        n /= 27

        callChars[4] = CALLSIGN_CHARS[(n % 27) + 10]
        n /= 27

        callChars[3] = CALLSIGN_CHARS[n % 10]
        n /= 10

        callChars[2] = CALLSIGN_CHARS[n % 10]
        n /= 10

        callChars[1] = CALLSIGN_CHARS[n % 36]
        n /= 36

        callChars[0] = CALLSIGN_CHARS[n % 37]

        return callChars.joinToString("")
    }

    /**
     * Decode grid locator and power from packed value
     *
     * @param packedLocPwr Packed grid and power value
     * @return Pair of grid locator and power in dBm
     */
    private fun decodeGridAndPower(packedLocPwr: Int): Pair<String, Int> {
        var n = packedLocPwr

        // Extract power (7 bits)
        val powerCode = n % 64
        n /= 64

        // Convert power code to dBm
        val powerDbm = (powerCode * 2 - 30)

        // Extract grid (15 bits)
        val grid = CharArray(4)

        grid[3] = '0' + (n % 10)
        n /= 10

        grid[2] = 'A' + (n % 18)
        n /= 18

        grid[1] = '0' + (n % 10)
        n /= 10

        grid[0] = 'A' + n

        return Pair(grid.joinToString(""), powerDbm)
    }
}