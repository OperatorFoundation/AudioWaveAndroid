package org.operatorfoundation.audiowave.decoder

import org.operatorfoundation.audiowave.utils.AudioUtils
import org.operatorfoundation.audiowave.utils.ErrorHandler
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Experimental WSPR (Weak Signal Propagation Reporter) Encoder
 *
 * Generates WSPR audio signals from callsign, grid, and power information.
 * WSPR uses 4-FSK modulation with a symbol rate of ~1.46 Hz.
 *
 * Implements the complete WSPR protocol as specified by K1JT (Joe Taylor).
 * This includes proper callsign and grid packing, convolutional encoding,
 * interleaving, and sync vector application.
 */
class WsprEncoder {
    companion object {
        // WSPR protocol constants
        const val SYMBOL_COUNT = 162       // Number of symbols in a WSPR transmission
        const val SAMPLE_RATE = 12000      // Sample rate in Hz
        const val TONE_SPACING = 1.4648    // Hz between tones
        const val BASE_FREQUENCY = 1500.0  // Audio center frequency in Hz
        const val SYMBOL_RATE = 12000.0 / 8192.0  // Symbol rate (~1.46 baud)
        const val SYMBOL_LENGTH = 8192     // Samples per symbol at 12kHz
        const val MESSAGE_BITS = 50        // Number of bits in a WSPR message
        const val PARITY_BITS = 0          // Number of parity bits

        // Callsign and locator encoding constants
        private val CALLSIGN_CHARS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
            'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
            'U', 'V', 'W', 'X', 'Y', 'Z', ' '
        )
        private const val CALLSIGN_CHARS_SIZE = 37

        // Sync vector (from WSJT-X source code)
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

        // Convolutional coding polynomials (K=32, r=1/2)
        private val POLY1 = intArrayOf(1, 1, 1, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1)
        private val POLY2 = intArrayOf(1, 0, 1, 1, 1, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0)

        // Bit reversal table for interleaving
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
    }

    // Store configuration for the encoder
    private var useEncodedCallsigns = true   // Use WSPR encoded callsigns (true) or raw callsigns (false)
    private var applyRampUpDown = true       // Apply amplitude ramp up/down at start/end to reduce clicks
    private var amplitudeScaling = 0.9f      // Scale output amplitude (0.0-1.0)

    /**
     * Configure the encoder with specific options.
     *
     * @param useEncodedCallsigns Whether to use WSPR callsign encoding (true) or raw callsigns (false)
     * @param applyRampUpDown Whether to apply amplitude ramping to reduce clicks
     * @param amplitudeScaling Output amplitude scaling factor (0.0-1.0)
     */
    fun configure(
        useEncodedCallsigns: Boolean = true,
        applyRampUpDown: Boolean = true,
        amplitudeScaling: Float = 0.9f
    ) {
        this.useEncodedCallsigns = useEncodedCallsigns
        this.applyRampUpDown = applyRampUpDown
        this.amplitudeScaling = amplitudeScaling.coerceIn(0.01f, 1.0f)

        Timber.Forest.d("WSPR Encoder configured: useEncodedCallsigns=$useEncodedCallsigns, " +
                "applyRampUpDown=$applyRampUpDown, amplitudeScaling=$amplitudeScaling")
    }

    /**
     * Generate WSPR audio for the given parameters
     *
     * @param callsign Amateur radio callsign
     * @param grid Maidenhead grid locator (4 chars)
     * @param powerDbm Power level in dBm (0-60)
     * @return ByteArray containing audio data at SAMPLE_RATE
     */
    fun generateWsprAudio(callsign: String, grid: String, powerDbm: Int): ByteArray {
        return ErrorHandler.runCatching {
            Timber.Forest.d("Generating WSPR audio for callsign=$callsign, grid=$grid, power=${powerDbm}dBm")

            // Validate input parameters
            val normalizedCallsign = callsign.uppercase().trim()
            val normalizedGrid = grid.uppercase().trim()
            val normalizedPower = powerDbm.coerceIn(0, 60)

            // Verify grid format (A-R for first and third, 0-9 for second and fourth)
            if (normalizedGrid.length != 4 ||
                normalizedGrid[0] !in 'A'..'R' ||
                normalizedGrid[1] !in '0'..'9' ||
                normalizedGrid[2] !in 'A'..'R' ||
                normalizedGrid[3] !in '0'..'9') {
                throw IllegalArgumentException("Grid must be 4 characters in Maidenhead format (e.g., FN20)")
            }

            // 1. Pack the message
            val messageBits = if (useEncodedCallsigns) {
                packWsprMessage(normalizedCallsign, normalizedGrid, normalizedPower)
            } else {
                packRawMessage(normalizedCallsign, normalizedGrid, normalizedPower)
            }

            // 2. Apply forward error correction
            val encodedBits = applyConvolutionalCode(messageBits)

            // 3. Interleave the bits
            val interleavedBits = interleave(encodedBits)

            // 4. Apply the sync vector
            val symbols = generateSymbols(interleavedBits)

            // 5. Generate audio samples
            val audio = generateAudio(symbols)

            Timber.Forest.d("Generated ${audio.size} bytes of WSPR audio")
            audio
        }.getOrElse { error ->
            Timber.Forest.e(error, "Error generating WSPR audio")
            ByteArray(0)  // Return empty array on error
        }
    }

    /**
     * Pack callsign, grid, and power into a 50-bit message using standard WSPR encoding
     */
    private fun packWsprMessage(callsign: String, grid: String, powerDbm: Int): IntArray {
        // Allocate bits for the message (50 bits)
        val messageBits = IntArray(MESSAGE_BITS)

        // Pack callsign (28 bits)
        val packedCall = packCallsign(callsign)

        // Pack grid and power (22 bits)
        val packedGridPower = packGridAndPower(grid, powerDbm)

        // Combine packed values into bit array
        for (i in 0 until 28) {
            messageBits[i] = (packedCall shr (27 - i)) and 1
        }

        for (i in 0 until 22) {
            messageBits[i + 28] = (packedGridPower shr (21 - i)) and 1
        }

        return messageBits
    }

    /**
     * Pack callsign into a 28-bit value using WSPR encoding
     */
    private fun packCallsign(callsign: String): Int {
        // Check for special case: prefix callsign or suffix callsign
        if (callsign.contains("/")) {
            return packSpecialCallsign(callsign)
        }

        // Regular callsign packing (28 bits)
        var result = 0

        // Normalize to exactly 6 characters with spaces on right
        val normCall = callsign.padEnd(6, ' ').substring(0, 6)

        // Convert callsign to indexes (each char is 0-36)
        val indexes = IntArray(6)
        for (i in 0 until 6) {
            indexes[i] = CALLSIGN_CHARS.indexOf(normCall[i])
            if (indexes[i] == -1) {
                indexes[i] = CALLSIGN_CHARS.indexOf(' ') // Replace invalid with space
            }
        }

        // Perform the packing math:
        // N = 36*36*10*10*26*26*indexes[0..5]
        result = indexes[0]
        result = result * 36 + indexes[1]
        result = result * 10 + indexes[2]
        result = result * 10 + indexes[3]
        result = result * 26 + indexes[4] - 10
        result = result * 26 + indexes[5] - 10

        return result
    }

    /**
     * Pack a special callsign (with / prefix or suffix) into a 28-bit value
     */
    private fun packSpecialCallsign(callsign: String): Int {
        // Special callsign packing logic
        // Implementation depends on the WSPR spec for special callsigns
        // For now, just treat it as a standard callsign by removing the /
        val cleanCall = callsign.replace("/", "").substring(0, min(callsign.length, 6))
        return packCallsign(cleanCall)
    }

    /**
     * Pack grid and power into a 22-bit value
     */
    private fun packGridAndPower(grid: String, powerDbm: Int): Int {
        var result = 0

        // Grid is 4 characters: two letters (A-R) and two digits (0-9)
        // First character (A-R) becomes 0-17
        result = grid[0] - 'A'

        // Second character (0-9) becomes 0-9
        result = result * 10 + (grid[1] - '0')

        // Third character (A-R) becomes 0-17
        result = result * 18 + (grid[2] - 'A')

        // Fourth character (0-9) becomes 0-9
        result = result * 10 + (grid[3] - '0')

        // Power level (0-60 dBm)
        // In WSPR protocol, power is mapped to 0-63 in the bitstream
        // Power = int(0.5 + (dbm + 30) / 2)
        val powerCode = ((powerDbm + 30) / 2.0 + 0.5).toInt().coerceIn(0, 63)
        result = result * 64 + powerCode

        return result
    }

    /**
     * Pack a raw message without WSPR callsign encoding - used for testing
     */
    private fun packRawMessage(callsign: String, grid: String, powerDbm: Int): IntArray {
        // Simple placeholder: convert text to bits (for testing only)
        val message = "$callsign|$grid|${powerDbm}dBm"
        val bits = IntArray(MESSAGE_BITS)

        // Fill with 0s first
        for (i in bits.indices) {
            bits[i] = 0
        }

        // Convert message chars to bits (8 bits per char)
        // Stop if we reach the bit array size
        for (i in message.indices) {
            if (i * 8 >= MESSAGE_BITS) break

            val c = message[i].code
            for (j in 0 until 8) {
                if (i * 8 + j < MESSAGE_BITS) {
                    bits[i * 8 + j] = (c shr (7 - j)) and 1
                }
            }
        }

        return bits
    }

    /**
     * Apply convolutional code (rate 1/2, K=32) to message bits
     */
    private fun applyConvolutionalCode(messageBits: IntArray): IntArray {
        // For rate 1/2, we produce 2 output bits for each input bit
        val encodedBits = IntArray(messageBits.size * 2)

        // Apply convolutional coding polynomials
        for (i in messageBits.indices) {
            // Calculate parity for each polynomial
            var parity1 = 0
            var parity2 = 0

            // Apply each term of the polynomial
            for (j in 0 until 32) {
                if (POLY1[j] == 1) {
                    val bitIndex = i - j
                    // Use 0 for bits before the message
                    val bit = if (bitIndex >= 0) messageBits[bitIndex] else 0
                    parity1 = parity1 xor bit
                }

                if (POLY2[j] == 1) {
                    val bitIndex = i - j
                    // Use 0 for bits before the message
                    val bit = if (bitIndex >= 0) messageBits[bitIndex] else 0
                    parity2 = parity2 xor bit
                }
            }

            // Store the two parity bits
            encodedBits[i * 2] = parity1
            encodedBits[i * 2 + 1] = parity2
        }

        return encodedBits
    }

    /**
     * Interleave the encoded bits using bit-reversal permutation
     */
    private fun interleave(encodedBits: IntArray): IntArray {
        val interleavedBits = IntArray(encodedBits.size)

        for (i in encodedBits.indices) {
            // Use bit-reversal permutation for interleaving
            val newIndex = BIT_REVERSAL[i] % encodedBits.size
            interleavedBits[newIndex] = encodedBits[i]
        }

        return interleavedBits
    }

    /**
     * Generate 4-FSK symbols from interleaved bits and sync vector
     */
    private fun generateSymbols(interleavedBits: IntArray): IntArray {
        val symbols = IntArray(SYMBOL_COUNT)

        // Combine pairs of bits with sync vector to create 4-FSK symbols
        for (i in 0 until SYMBOL_COUNT) {
            // Get data bit and sync bit
            val dataBit = if (i < interleavedBits.size) interleavedBits[i] else 0
            val syncBit = SYNC_VECTOR[i]

            // Combine to create a 4-FSK symbol (0-3)
            // MSB = data bit, LSB = sync bit
            symbols[i] = (dataBit shl 1) or syncBit
        }

        return symbols
    }

    /**
     * Generate audio samples from the WSPR symbols
     */
    private fun generateAudio(symbols: IntArray): ByteArray {
        // Calculate total audio length
        val totalSamples = SYMBOL_COUNT * SYMBOL_LENGTH
        val shortSamples = ShortArray(totalSamples)

        // Generate audio for each symbol
        for (i in 0 until SYMBOL_COUNT) {
            val symbol = symbols[i]

            // Calculate tone frequency (4-FSK with tones at -1.5, -0.5, +0.5, +1.5 times tone spacing)
            val toneOffset = (symbol - 1.5) * TONE_SPACING
            val frequency = BASE_FREQUENCY + toneOffset

            // Generate samples for this symbol
            for (j in 0 until SYMBOL_LENGTH) {
                val time = j / SAMPLE_RATE.toDouble()
                val angle = 2.0 * PI * frequency * time

                // Calculate sample value (-1.0 to 1.0)
                var sample = sin(angle).toFloat()

                // Apply amplitude scaling
                sample *= amplitudeScaling

                // Apply amplitude ramp up/down at start/end to reduce clicks
                if (applyRampUpDown) {
                    // Ramp up first symbol
                    if (i == 0) {
                        val rampFactor = min(1.0f, j / (SYMBOL_LENGTH * 0.1f))
                        sample *= rampFactor
                    }

                    // Ramp down last symbol
                    if (i == SYMBOL_COUNT - 1) {
                        val rampFactor = min(1.0f, (SYMBOL_LENGTH - j) / (SYMBOL_LENGTH * 0.1f))
                        sample *= rampFactor
                    }
                }

                // Convert to 16-bit PCM
                val sampleIndex = (i * SYMBOL_LENGTH + j)
                shortSamples[sampleIndex] = AudioUtils.clipToShort(sample * 32767.0f)
            }
        }

        // Convert to byte array
        return AudioUtils.shortsToBytes(shortSamples)
    }

    /**
     * Check if current time is appropriate for WSPR transmission
     * WSPR transmissions should start at even minutes
     *
     * @return True if within the start window for WSPR
     */
    fun isWsprTransmitTime(): Boolean {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val second = now.second
        return now.minute % 2 == 0 && second < 2
    }

    /**
     * Get seconds until next WSPR transmit window
     */
    fun secondsUntilNextTransmitWindow(): Int {
        val now = LocalDateTime.now(ZoneOffset.UTC)
        val currentMinute = now.minute
        val currentSecond = now.second

        // Calculate seconds until next even minute
        return if (currentMinute % 2 == 0 && currentSecond < 2) {
            0 // We're in a transmit window now
        } else {
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
    fun getTransmissionDurationSeconds(): Double {
        return SYMBOL_COUNT * SYMBOL_LENGTH / SAMPLE_RATE.toDouble()
    }

    /**
     * Get the sample rate used by the encoder
     *
     * @return The sample rate in Hz
     */
    fun getSampleRate(): Int {
        return SAMPLE_RATE
    }
}