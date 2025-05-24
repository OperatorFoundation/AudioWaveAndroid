package org.operatorfoundation.audiowave.codec.codecs.wspr

import org.operatorfoundation.audiowave.codec.AudioDecoder
import org.operatorfoundation.audiowave.utils.AudioUtils
import org.operatorfoundation.audiowave.utils.ErrorHandler
import org.operatorfoundation.audiowave.utils.SignalProcessor
import timber.log.Timber
import kotlin.math.*
import java.util.PriorityQueue

/**
 * Implementation of the AudioDecoder interface for WSPR protocol.
 *
 * This class decodes WSPR audio signals into callsign, grid, and power information
 * using the protocol standards defined in WsprProtocol.
 */
class WsprDecoder : AudioDecoder
{
    override val id: String = "wspr_decoder"
    override val name: String = "WSPR Decoder"
    override val description: String = "Decodes Weak Signal Propagation Reporter (WSPR) signals"

    private val signalProcessor = SignalProcessor()
    private var centerFrequency = WsprProtocol.BASE_FREQUENCY
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
    )
    {
        override fun toString(): String
        {
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
     * Decode WSPR message from audio data.
     *
     * @param audioData The raw audio data to decode
     * @return Decoded data containing WSPR message information as UTF-8 encoded text
     */
    override fun decode(audioData: ByteArray): ByteArray {
        Timber.d("Starting WSPR decoding process")

        return ErrorHandler.runCatching {
            // Convert byte array to short for processing
            val samples = AudioUtils.bytesToShorts(audioData)

            // 1. Check if we have enough data for a full WSPR transmission
            val expectedSamples = WsprProtocol.TRANSMISSION_LENGTH_SECONDS * WsprProtocol.SAMPLE_RATE
            if (samples.size < expectedSamples)
            {
                Timber.w("Audio data too short for WSPR decoding: ${samples.size} samples, need $expectedSamples")
                return@runCatching "Audio data too short for WSPR decoding".toByteArray()
            }

            // 2. Check if timing is appropriate (if expected start time is set)
            if (expectedStartTime > 0)
            {
                val now = System.currentTimeMillis()
                val timeDiff = abs(now - expectedStartTime)

                // WSPR requires accurate timing (±1 second)
                if (timeDiff > 5000) // Allow 5 second grace period
                {
                    Timber.w("Audio data timing offset: ${timeDiff/1000}s from expected start")
                }
            }

            // 3. Prepare for decoding
            val decodedMessages = mutableListOf<DecodedMessage>()

            // 4. Decode (either single or multiple signals)
            if (multiDecoderEnabled)
            {
                // Decode multiple signals
                val signals = detectWsprSignals(samples)
                Timber.d("Detected ${signals.size} potential WSPR signals")

                // Process each signal
                for ((i, signal) in signals.withIndex())
                {
                    if (i >= maxDecodes) break

                    try {
                        val message = decodeWsprSignal(samples, signal.first, signal.second)

                        if (message != null)
                        {
                            decodedMessages.add(message)
                            Timber.d("Decoded WSPR message: $message")
                        }
                    }
                    catch (e: Exception)
                    {
                        Timber.e(e, "Error decoding WSPR signal at ${signal.first} Hz")
                    }
                }
            }
            else
            {
                // Decode single signal at center frequency
                try {
                    val message = decodeWsprSignal(samples, centerFrequency, 0.0)
                    if (message != null) {
                        decodedMessages.add(message)
                        Timber.d("Decoded WSPR message: $message")
                    }
                }
                catch (e: Exception) {
                    Timber.e(e, "Error decoding WSPR signal at $centerFrequency Hz")
                }
            }

            // 5. Format response
            if (decodedMessages.isEmpty())
            {
                Timber.w("No WSPR signals decoded")
                return@runCatching "No WSPR signals decoded".toByteArray()
            }

            // Format the decoded messages
            val result = StringBuilder()
            for (message in decodedMessages)
            {
                result.append(message.toString()).append("\n")
            }

            result.toString().trim().toByteArray()
        }.getOrElse { error ->
            Timber.e(error, "Error decoding WSPR data")
            "Error decoding WSPR: ${error.message}".toByteArray()
        }
    }

    /**
     * Detect multiple WSPR signals in the audio data.
     *
     * @param samples Audio samples to analyze
     * @return List of potential signals as pairs of (frequency, SNR)
     */
    private fun detectWsprSignals(samples: ShortArray): List<Pair<Double, Double>>
    {
        // Apply bandpass filter covering typical WSPR range
        val lowerFreq = (centerFrequency - 200.0).toFloat()
        val upperFreq = (centerFrequency + 200.0).toFloat()

        val filteredSamples = signalProcessor.applyBandpassFilter(samples, lowerFreq, upperFreq,
            WsprProtocol.SAMPLE_RATE)

        // Compute spectrum with high resolution
        val fftSize = 32768
        val sampleRate = WsprProtocol.SAMPLE_RATE.toDouble()
        val spectrumResult = computeSpectrum(filteredSamples, fftSize)
        val spectrum = spectrumResult.first
        val freqBins = spectrumResult.second

        // Compute noise floor
        val noiseFloor = calculateNoiseFloor(spectrum)

        // Find peaks above threshold
        val peaks = PriorityQueue<Pair<Double, Double>>(compareByDescending { it.second }) // Sort by SNR descending

        val minBin = (lowerFreq / (sampleRate / fftSize)).toInt().coerceIn(0, spectrum.size - 1)
        val maxBin = (upperFreq / (sampleRate / fftSize)).toInt().coerceIn(0, spectrum.size - 1)

        // Minimum peak width in bins (to avoid detecting noise spikes)
        val minPeakWidth = (4.0 / (sampleRate / fftSize)).toInt()

        var i = minBin
        while (i <= maxBin)
        {
            if (spectrum[i] > noiseFloor * (1.0 + signalThreshold))
            {
                // Found potential peak
                var peakBin = i
                var peakValue = spectrum[i]

                // Find the exact peak
                var j = i + 1
                while (j <= maxBin && spectrum[j] >= spectrum[j - 1])
                {
                    if (spectrum[j] > peakValue)
                    {
                        peakValue = spectrum[j]
                        peakBin = j
                    }

                     j++
                }

                // Calculate peak width and verify it's wide enough
                var lowerEdge = peakBin
                while (lowerEdge > minBin && spectrum[lowerEdge] > noiseFloor * 1.5) { lowerEdge-- }

                var upperEdge = peakBin
                while (upperEdge < maxBin && spectrum[lowerEdge] > noiseFloor * 1.5) { upperEdge++ }

                val peakWidth = upperEdge - lowerEdge

                if (peakWidth >= minPeakWidth)
                {
                    // Calculate frequency and SNR
                    val frequency = freqBins[peakBin]
                    val snrDb = 10.0 * log10(peakValue / noiseFloor)

                    if (snrDb > WsprProtocol.SNR_THRESHOLD)
                    {
                        peaks.add(Pair(frequency, snrDb))
                    }
                }

                // Skip to after this peak
                i = upperEdge + 1
            }
            else
            {
                i++
            }
        }

        // Return the top signals
        val result = mutableListOf<Pair<Double, Double>>()
        while (result.size < maxDecodes && peaks.isNotEmpty())
        {
            result.add(peaks.poll())
        }

        return result
    }

    /**
     * Compute spectrum of audio samples.
     *
     * @param samples The audio samples
     * @param fftSize FFT size
     * @return Pair of spectrum magnitudes and frequency bins
     */
    private fun computeSpectrum(samples: ShortArray, fftSize: Int): Pair<DoubleArray, DoubleArray>
    {
        // Prepare FFT input
        val paddedSamples = ShortArray(fftSize)
        samples.copyInto(paddedSamples, 0, 0, min(samples.size, fftSize))

        // Apply window function
        val windowedSamples = DoubleArray(fftSize)
        for (i in 0 until fftSize)
        {
            val windowFactor = if (i < samples.size)
            {
                0.56 - 0.46 * cos(2.0 * PI * i / (samples.size - 1)) // Hamming window
            }
            else { 0.0 }

            windowedSamples[i] = paddedSamples[i] * windowFactor
        }

        // Compute FFT
        val fftResult = signalProcessor.fftComplex(windowedSamples)

        // Calculate magnitude spectrum
        val spectrumSize = fftSize / 2
        val spectrum = DoubleArray(spectrumSize)
        for (i in 0 until spectrumSize)
        {
            val real = fftResult[i].real
            val imag = fftResult[i].imag
            spectrum[i] = real * real + imag * imag // Magnitude squared
        }

        // Calculate frequency bins
        val freqBins = DoubleArray(fftSize / 2)
        val freqStep = WsprProtocol.SAMPLE_RATE.toDouble() / fftSize
        for (i in freqBins.indices)
        {
            freqBins[i] = i * freqStep
        }

        return Pair(spectrum, freqBins)
    }

    /**
     * Calculate noise floor from spectrum.
     *
     * @param spectrum The magnitude spectrum
     * @return The estimated noise floor level
     */
    private fun calculateNoiseFloor(spectrum: DoubleArray): Double
    {
        // Sort the spectrum values
        val sortedSpectrum = spectrum.copyOf()
        sortedSpectrum.sort()

        // Use median to estimate noise floor
        val medianIndex = sortedSpectrum.size / 2
        return sortedSpectrum[medianIndex]
    }

    /**
     * Decode a single WSPR signal.
     *
     * @param samples The audio samples
     * @param frequency The center frequency of the signal
     * @param snrDb The estimated SNR in dB
     * @return The decoded message or null if decoding failed
     */
    fun decodeWsprSignal(samples: ShortArray, frequency: Double, snrDb: Double): DecodedMessage?
    {
        // 1. Apply bandpass filter centered on the signal
        val filteredSamples = signalProcessor.applyBandpassFilter(
            samples,
            (frequency - WsprProtocol.WSPR_BANDWIDTH/2).toFloat(),
            (frequency + WsprProtocol.WSPR_BANDWIDTH/2).toFloat(),
            WsprProtocol.SAMPLE_RATE
        )

        // 2. Synchronize to find optimal symbol boundaries
        val syncResult = synchronizeToSymbols(filteredSamples, frequency)
        if (syncResult == null)
        {
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
        val deinterleavedBits = WsprProtocol.deinterleaveBits(bits)

        // 6. Apply convolutional decoding
        val messageBits = applyViterbiDecoding(deinterleavedBits)

        // 7. Decode the message
        return decodeWspMessage(messageBits, frequency, snrDb)
    }

    /**
     * Synchronize to symbol boundaries using sync pattern correlation
     *
     * @param samples The audio samples
     * @param frequency The signal frequency
     * @return Pair of start sample and sync quality, or null if sync failed
     */
    private fun synchronizeToSymbols(samples: ShortArray, frequency: Double): Pair<Int, Double>?
    {
        // Create sync template: the expected signal pattern
        val syncTemplateLength = WsprProtocol.SYMBOL_COUNT * WsprProtocol.SYMBOL_LENGTH
        val syncTemplate = ShortArray(syncTemplateLength)

        // Generate the sync template based on the sync vector
        for (i in 0 until WsprProtocol.SYMBOL_COUNT)
        {
            val symbol = WsprProtocol.SYNC_VECTOR[i]

            // Calculate tone frequency
            val toneOffset = (symbol - 0.5) * WsprProtocol.TONE_SPACING
            val toneFreq = frequency + toneOffset

            // Generate symbol waveforms
            for (j in 0 until WsprProtocol.SYMBOL_LENGTH)
            {
                val time = j / WsprProtocol.SAMPLE_RATE.toDouble()
                val angle = 2.0 * PI * toneFreq * time
                val sample = (sin(angle) * 32767.0).toInt().toShort()
                syncTemplate[i * WsprProtocol.SYMBOL_LENGTH + j] = sample
            }
        }

        // Perform correlation over a range of offsets
        val maxOffset = WsprProtocol.SYMBOL_LENGTH * 2 // Search range: 2 symbols
        var bestOffset = 0
        var bestCorrelation = Double.NEGATIVE_INFINITY

        // Try different offsets
        for (offset in 0 until maxOffset)
        {
            if (offset + syncTemplate.size > samples.size) break

            var correlation = 0.0
            for (i in syncTemplate.indices)
            {
                correlation += (samples[offset + i].toDouble() * syncTemplate[i].toDouble())
            }

            if (correlation > bestCorrelation)
            {
                bestCorrelation = correlation
                bestOffset = offset
            }
        }

        // Normalize correlation to produce quality metric
        val sampleEnergy = samples.sumOf { it.toDouble() * it.toDouble() }
        val templateEnergy = syncTemplate.sumOf { it.toDouble() * it.toDouble() }
        val normalizedCorrelation = bestCorrelation / sqrt(sampleEnergy * templateEnergy)

        // Check if correlation is good enough
        return if (normalizedCorrelation > 0.15) // Threshold for good sync
        {
            Pair(bestOffset, normalizedCorrelation)
        }
        else { null }
    }

    /**
     * Extract symbols from synchronized audio data
     *
     * @param samples Audio samples
     * @param frequencyHz Center frequency in Hz
     * @param startSample Starting sample for synchronized data
     * @return Array of demodulated symbols
     */
    private fun extractSymbols(samples: ShortArray, frequencyHz: Double, startSample: Int): IntArray
    {
        val symbols = IntArray(WsprProtocol.SYMBOL_COUNT)

        // Get tone frequencies
        val toneFrequencies = WsprProtocol.calculateToneFrequencies(frequencyHz)

        // For each symbol position
        for (i in 0 until WsprProtocol.SYMBOL_COUNT)
        {
            val symbolStart = startSample + i * WsprProtocol.SYMBOL_LENGTH

            // Extract this symbol's samples
            val symbolSamples = ShortArray(WsprProtocol.SYMBOL_LENGTH)
            for (j in 0 until WsprProtocol.SYMBOL_LENGTH)
            {
                val sampleIndex = symbolStart + j
                if (sampleIndex < samples.size)
                {
                    symbolSamples[j] = samples[sampleIndex]
                }
            }

            // Measure energy at each tone frequency
            val toneEnergies = DoubleArray(4)
            for (toneIndex in 0 until 4)
            {
                toneEnergies[toneIndex] = signalProcessor.calculateEnergy(symbolSamples, toneFrequencies[toneIndex].toFloat(),
                    WsprProtocol.SAMPLE_RATE)
            }

            // Select tone with maximum energy
            var maxEnergy = Double.NEGATIVE_INFINITY
            var bestTone = 0
            for (toneIndex in 0 until 4)
            {
                if (toneEnergies[toneIndex] > maxEnergy)
                {
                    maxEnergy = toneEnergies[toneIndex]
                    bestTone = toneIndex
                }
            }

            symbols[i] = bestTone
        }

        return symbols
    }

    /**
     * Extract bits from demodulated symbols.
     *
     * @param symbols Array of symbols (0-3)
     * @return Array of bits
     */
    private fun extractBitsFromSymbols(symbols: IntArray): IntArray
    {
        val bits = IntArray(symbols.size * 2)

        for (i in symbols.indices)
        {
            // Extract two bits from each symbol
            bits[i * 2] = (symbols[i] shr 1) and 1
            bits[i * 2 + 1] = symbols[i] and 1
        }

        return bits
    }

    /**
     * Apply Viterbi decoding to extract message bits.
     *
     * @param encodedBits The encoded bits
     * @return The decoded message bits
     */
    private fun applyViterbiDecoding(encodedBits: IntArray): IntArray
    {
        // FIXME: simplified implementation for prototype purposes. Full Viterbi decoder not implemented

        // For now, just use a simple majority vote for each bit position
        val messageBits = IntArray(WsprProtocol.MESSAGE_BITS)

        // Process each message bit
        for (i in 0 until WsprProtocol.MESSAGE_BITS)
        {
            // Count votes from each encoded bit pair
            var voteCount = 0

            // Look at all bit pairs that depend on this message bit
            for (j in 0 until 32)
            {
                if (i + j < encodedBits.size / 2)
                {
                    val bitPairIndex = (i + j) * 2

                    // Check if these polynomials include the current bit
                    if (WsprProtocol.CONVOLUTIONAL_POLY1[j] == 1 && encodedBits[bitPairIndex] == 1) voteCount++
                    if (WsprProtocol.CONVOLUTIONAL_POLY2[j] == 1 && encodedBits[bitPairIndex + 1] == 1) voteCount++
                }
            }

            // Decide bit value based on votes
            messageBits[i] = if (voteCount >= 32) 1 else 0
        }

        return messageBits
    }

    /**
     * Decode WSPR message from bits using protocol utilities.
     *
     * @param messageBits The message bits
     * @param frequencyHz The signal frequency in Hz
     * @param snrDb The signal SNR in dB
     * @return Decoded message object
     */
    private fun decodeWspMessage(messageBits: IntArray, frequencyHz: Double, snrDb: Double): DecodedMessage?
    {
        // Pack bits into values
        var packedCall = 0
        for (i in 0 until 28)
        {
            packedCall = (packedCall shl 1) or messageBits[i]
        }

        var packedLocPwr = 0
        for (i in 0 until 22)
        {
            packedLocPwr = (packedLocPwr shl 1) or messageBits[i + 28]
        }

        // Decode callsign
        val callsign = WsprProtocol.unpackCallsign(packedCall)

        // Decode grid and power
        val gridAndPower = WsprProtocol.unpackGridAndPower(packedLocPwr)
        val grid = gridAndPower.first
        val powerDbm = gridAndPower.second

        // Return decoded message
        return DecodedMessage(
            callsign = callsign,
            grid = grid,
            powerDbm = powerDbm,
            snrDb = snrDb,
            frequencyHz = frequencyHz
        )
    }

}