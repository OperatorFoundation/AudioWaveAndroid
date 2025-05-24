package org.operatorfoundation.audiowave.codec.codecs.wspr

import org.operatorfoundation.audiowave.codec.AudioEncoder
import org.operatorfoundation.audiowave.utils.AudioUtils
import org.operatorfoundation.audiowave.utils.ErrorHandler
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Implementation of the AudioEncoder interface for WSPR protocol.
 *
 * This class adapts the WsprEncoder functionality to the AudioEncoder interface,
 * making it compatible with the codec architecture. It encodes callsign, grid,
 * and power information into WSPR audio signals following the protocol standards.
 */
class WsprEncoder : AudioEncoder
{
    override val id: String = "wspr_encoder"
    override val name: String = "WSPR Encoder"
    override val description: String = "Encodes data into WSPR (Weak Signal Propagation Reporter) audio signals"

    // Configuration parameters
    private var useEncodedCallsigns = true      // Use WSPR encoded callsigns (true) or raw callsigns (false)
    private var applyRampUpDown = true          // Apply amplitude ramp up/down at start/end to reduce clicks
    private var amplitudeScaling = 0.9f         // Scale output amplitude (0.0-1.0)
    private var centerFrequency = WsprProtocol.BASE_FREQUENCY   // Center frequency in Hz

    /**
     * Encode data into WSPR audio signal.
     *
     * The input data is expected to contain a callsign, grid, and power information
     * in the format: "CALLSIGN|GRID|POWER"  (e.g., "W1AW|FN31|10")
     *
     * @param data The data to encode (expected to contain callsign, grid, and power information)
     * @return The audio data (PCM format)
     */
    override fun encode(data: ByteArray): ByteArray {
        return ErrorHandler.runCatching {
            // Parse the input data
            val dataString = String(data)
            val parts = dataString.split("|")

            if (parts.size < 3)
            {
                throw IllegalArgumentException("Input data must be in format 'CALLSIGN|GRID|POWER'")
            }

            val callsign = parts[0]
            val grid = parts[1]
            val powerDbm = parts[2].replace("dBm", "").trim().toInt()

            // Generate WSPR audio
            generateWsprAudio(callsign, grid, powerDbm)
        }.getOrElse { error ->
            Timber.e(error, "Error encoding WSPR data")
            ByteArray(0)
        }
    }

    /**
     * Configure the encoder with specific parameters.
     *
     * @param params Map of parameter names to parameter values:
     *  - "useEncodedCallsigns": Boolean (whether to use WSPR callsign encoding)
     *  - "applyRampUpDown": Boolean (whether to apply amplitude ramping)
     *  - "amplitudeScaling": Float (output amplitude scaling factor, 0.0-1.0)
     *  - "centerFrequency": Float (center frequency in Hz)
     */
    override fun configure(params: Map<String, Any>) {
        params["useEncodedCallsigns"]?.let {
            useEncodedCallsigns = it as Boolean
            Timber.d("WSPR encoded callsigns: $useEncodedCallsigns")
        }

        params["applyRampUpDown"]?.let {
            applyRampUpDown = it as Boolean
            Timber.d("WSPR apply ramp up/down: $applyRampUpDown")
        }

        params["amplitudeScaling"]?.let {
            amplitudeScaling = (it as Number).toFloat().coerceIn(0.01f, 1.0f)
            Timber.d("WSPR amplitude scaling: $amplitudeScaling")
        }

        params["centerFrequency"]?.let {
            centerFrequency = (it as Number).toDouble()
            Timber.d("WSPR center frequency: $centerFrequency Hz")
        }
    }

    /**
     * Generate WSPR audio for the given parameters
     *
     * @param callsign Amateur radio callsign
     * @param grid Maidenhead grid locator (4 chars)
     * @param powerDbm Power level in dBm (0-60)
     * @return ByteArray containing audio data at SAMPLE_RATE
     */
    private fun generateWsprAudio(callsign: String, grid: String, powerDbm: Int): ByteArray
    {
        Timber.d("Generating WSPR audio for callsign=$callsign, grid=$grid, power=${powerDbm}dBm")

        // Validate input parameters
        val normalizedCallsign = callsign.uppercase().trim()
        val normalizedGrid = grid.uppercase().trim()
        val normalizedPower = WsprProtocol.validatePowerLevel(powerDbm)

        // Verify grid format using protocol validation
        if (!WsprProtocol.isValidMaidenheadGrid(normalizedGrid))
        {
            throw IllegalArgumentException("Grid must be 4 characters in Maidenhead format (e.g., FN20)")
        }

        // 1. Pack the message
        val messageBits = if (useEncodedCallsigns)
        {
            packWsprMessage(normalizedCallsign, normalizedGrid, normalizedPower)
        }
        else
        {
            packRawMessage(normalizedCallsign, normalizedGrid, normalizedPower)
        }

        // 2. Apply forward error correction using protocol utilities
        val encodedBits = WsprProtocol.applyConvolutionalEncoding(messageBits)

        // 3. Interleave the bits using protocol utilities
        val interleavedBits = WsprProtocol.interleaveBits(encodedBits)

        // 4. Apply the sync vector
        val symbols = WsprProtocol.generateSymbols(interleavedBits)

        // 5. Generate audio samples
        return generateAudio(symbols)
    }

    /**
     * Pack callsign, grid, and power into a 50-bit message using standard WSPR encoding
     */
    private fun packWsprMessage(callsign: String, grid: String, powerDbm: Int): IntArray
    {
        // Allocate bits for the message (50 bits)
        val messageBits = IntArray(WsprProtocol.MESSAGE_BITS)

        // Pack the callsign (28 bits)
        val packedCallsign = WsprProtocol.packCallsign(callsign)

        // Pack the grid and power (22 bits)
        val packedGridPower = WsprProtocol.packGridAndPower(grid, powerDbm)

        // Combine packed values into a bit array
        for (i in 0 until 28)
        {
            messageBits[i] = (packedCallsign shr (27 - i)) and 1
        }

        for (i in 0 until 22)
        {
            messageBits[i + 28] = (packedGridPower shr (21 - i)) and 1
        }

        return messageBits
    }

    /**
     * Pack a raw message without WSPR callsign encoding - used for testing
     */
    private fun packRawMessage(callsign: String, grid: String, powerDbm: Int): IntArray
    {
        // Convert text to bits (for testing only)
        val message = "$callsign|$grid|${powerDbm}dBm"
        val bits = IntArray(WsprProtocol.MESSAGE_BITS)

        // Fill with 0s first
        for (i in bits.indices)
        {
            bits[i] = 0
        }

        // Convert message chars to bits (8 bits per char)
        for (i in message.indices)
        {
            if (i * 8 >= WsprProtocol.MESSAGE_BITS) break

            val c = message[i].code
            for (j in 0 until 8)
            {
                if (i * 8 + j < WsprProtocol.MESSAGE_BITS)
                {
                    bits[i * 8 + j] = (c shr (7 - j)) and 1
                }
            }
        }

        return bits
    }

    /**
     * Generate audio samples from the WSPR symbols
     */
    private fun generateAudio(symbols: IntArray): ByteArray
    {
        // Calculate the total audio length
        val totalSamples = WsprProtocol.SYMBOL_COUNT * WsprProtocol.SYMBOL_LENGTH
        val shortSamples = ShortArray(totalSamples)

        // Get tone frequencies
        val toneFrequencies = WsprProtocol.calculateToneFrequencies(centerFrequency)

        // Generate audio for each symbol
        for (i in 0 until WsprProtocol.SYMBOL_COUNT)
        {
            val symbol = symbols[i]
            val frequency = toneFrequencies[symbol]

            // Generate samples for this symbol
            for (j in 0 until WsprProtocol.SYMBOL_LENGTH)
            {
                val time = j / WsprProtocol.SAMPLE_RATE.toDouble()
                val angle = 2.0 * PI * frequency * time

                // Calculate the sample value (-1.0 to 1.0)
                var sample = sin(angle).toFloat()

                // Apply amplitude scaling
                sample *= amplitudeScaling

                // Apply amplitude ramp up/down at start/end to reduce clicks
                if (applyRampUpDown)
                {
                    // Ramp up first symbol
                    if (i == 0)
                    {
                        val rampFactor = min(1.0f, j / (WsprProtocol.SYMBOL_LENGTH * 0.1f))
                        sample *= rampFactor
                    }

                    // Ramp down last symbol
                    if (i == WsprProtocol.SYMBOL_COUNT - 1)
                    {
                        val rampFactor = min(1.0f, (WsprProtocol.SYMBOL_LENGTH - j) / (WsprProtocol.SYMBOL_LENGTH * 0.1f))
                        sample *= rampFactor
                    }
                }

                // Convert to 16-bit PCM
                val sampleIndex = (i * WsprProtocol.SYMBOL_LENGTH + j)
                shortSamples[sampleIndex] = AudioUtils.clipToShort(sample * 32767.0f)
            }
        }

        // Convert to byte array
        return AudioUtils.shortsToBytes(shortSamples)
    }


    /**
     * Check if current time is appropriate for WSPR transmission.
     * WSPR transmission should start at even minutes.
     *
     * @return True if within the start window for WSPR
     */
    fun isWsprTransmitTime(): Boolean
    {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val second = now.second
        return now.minute % 2 == 0 && second < 2
    }

    /**
     * Get seconds until next WSPR transmit window.
     */
    fun secondsUntilNextTransmitWindow(): Int
    {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val currentMinute = now.minute
        val currentSecond = now.second

        // Calculate seconds until next even minute
        return if (currentMinute % 2 == 0 && currentSecond < 2)
        {
            0 // We're in a transmit window now
        }
        else
        {
            val nextEvenMinute = if (currentMinute % 2 == 0) currentMinute + 2 else currentMinute + 1
            val minutesToWait = (nextEvenMinute - currentMinute) % 60
            minutesToWait * 60 - currentSecond
        }
    }

    /**
     * Get the duration of a WSPR transmission in seconds
     *
     * @return The duration in seconds
     */
    fun getTransmissionDurationSeconds(): Double
    {
        return WsprProtocol.SYMBOL_COUNT * WsprProtocol.SYMBOL_LENGTH / WsprProtocol.SAMPLE_RATE.toDouble()
    }

    /**
     * Get the sample rate used by the encoder.
     *
     * @return The sample rate in Hz
     */
    fun getSampleRate(): Int
    {
        return WsprProtocol.SAMPLE_RATE
    }

}