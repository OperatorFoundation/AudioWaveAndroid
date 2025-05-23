package org.operatorfoundation.audiowave.codec.codecs.wspr

/**
 * Shared protocol constants and utilities for WSPR (Weak Signal Propagation Reporter).
 *
 * This object contains all of the constants, lookup tables, and shared utilities
 * specific to both the WSPR encoder and decoder.
 *
 * All constants are based on the WSPR specifications as implemented by WSJT-X.
 */
object WsprProtocol
{
    // Basic protocol parameters
    const val SYMBOL_COUNT = 162                    // Number of symbols in a WSPR transmission
    const val SAMPLE_RATE = 12000                   // Sample rate in Hz
    const val TONE_SPACING = 1.4648                 // Hz between tones
    const val BASE_FREQUENCY = 1500.0               // Audio center frequency in Hz
    const val SYMBOL_RATE = 12000.0 / 8192.0        // Symbol rate (~1.46 baud)
    const val SYMBOL_LENGTH = 8192                  // Samples per symbol at 12kHz
    const val MESSAGE_BITS = 50                     // Number of bits in the WSPR message
    const val TRANSMISSION_LENGTH_SECONDS = 120     // 2 minutes
    const val SNR_THRESHOLD = -25.0                 // Minimum SNR for detection in dB
    const val WSPR_BANDWIDTH = 6.0                  // WSPR signal bandwidth in Hz

    /**
     * Sync vector used in WSPR protocol (from WSJT-X source code).
     * This 162-element array defines the sync pattern that's combined with data bits.
     */
    val SYNC_VECTOR = intArrayOf(
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

    /**
     * Callsign and locator encoding/decoding character set.
     * Maps characters to their encoded values in WSPR messages.
     */
    val CALLSIGN_CHARS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z', ' '
    )

    /**
     * Convolutional coding polynomials for forward error correction.
     * WSPR uses a rate 1/2, constraint length K=32 convolutional code.
     */
    val CONVOLUTIONAL_POLY1 = intArrayOf(
        1, 1, 1, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0,
        1, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1
    )

    val CONVOLUTIONAL_POLY2 = intArrayOf(
        1, 0, 1, 1, 1, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 0,
        1, 0, 1, 0, 1, 0, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0
    )

    /**
     * Bit reversal table for interleaving/deinterleaving operations.
     * This 256-element lookup table is used to scramble bit order for error correction.
     */
    val BIT_REVERSAL_TABLE = intArrayOf(
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

    /**
     * Calculate the four tone frequencies for 4-FSK modulation.
     *
     * @param centerFrequency The center frequency in Hz
     * @return Array of four tone frequencies
     */
    fun calculateToneFrequencies(centerFrequency: Double): DoubleArray
    {
        return DoubleArray(4) { toneIndex ->
            centerFrequency + (toneIndex - 1.5) * TONE_SPACING
        }
    }

    /**
     * Validate a Maidenhead grid locater format.
     *
     * @param grid The grid locator to validate
     * @return True if the grid is in valid Maidenhead format
     */
    fun isValidMaidenheadGrid(grid: String): Boolean
    {
        if (grid.length != 4) return false

        return grid[0] in 'A'..'R' &&
                grid[1] in '0'..'9' &&
                grid[2] in 'A'..'R' &&
                grid[3] in '0'..'9'
    }

    /**
     * Validate power level for WSPR protocol.
     *
     * @param powerDbm Power in dBm
     * @return Validated power level constrained to WSPR range
     */
    fun validatePowerLevel(powerDbm: Int): Int
    {
        return powerDbm.coerceIn(0, 60)
    }

    /**
     * Convert power in dBm to WSPR power code (0-63)
     *
     * @param powerDbm Power level in dBm
     * @return WSPR power code
     */
    fun powerDbmToPowerCode(powerDbm: Int): Int
    {
        return ((powerDbm + 30) / 2.0 + 0.5).toInt().coerceIn(0, 63)
    }

    /**
     * Convert WSPR power code back to dBm.
     *
     * @param powerCode The WSPR power code to convert (0-63)
     * @return Power level in dBm
     */
    fun powerCodeToPowerDbm(powerCode: Int): Int
    {
        return (powerCode * 2 - 30)
    }

    /**
     * Pack a callsign into a 28-bit integer value.
     *
     * @param callsign The callsign to pack
     * @return Packed callsign as 28-bit integer
     */
    fun packCallsign(callsign: String): Int
    {
        // Handle special callsigns with '/' separator
        if (callsign.contains("/"))
        {
            return packSpecialCallsign(callsign)
        }

        // Normalize to exactly 6 characters with spaces on right
        val normalizedCallsign = callsign.padEnd(6, ' ').substring(0, 6)

        // Convert callsign to indexes (each char is 0-36)
        val indexes = IntArray(6) { index ->
            val charIndex = CALLSIGN_CHARS.indexOf(normalizedCallsign[index])
            if (charIndex == -1) CALLSIGN_CHARS.indexOf(' ') else charIndex
        }

        // Perform the packing math: N = 36*36*10*10*26*26*indexes[0..5]
        var result = indexes[0]
        result = result * 36 + indexes[1]
        result = result * 10 + indexes[2]
        result = result * 10 + indexes[3]
        result = result * 26 + (indexes[4] - 10)
        result = result * 26 + (indexes[5] - 10)

        return result
    }

    /**
     * Unpack a 28-bit integer back to a callsign string.
     *
     * @param packedCallsign Packed callsign value
     * @return Decoded callsign string
     */
    fun unpackCallsign(packedCallsign: Int): String
    {
        var remainingValue = packedCallsign
        val callChars = CharArray(6)

        // Decode from right to left (reverse of packing process)
        callChars[5] = CALLSIGN_CHARS[(remainingValue % 27) + 10]
        remainingValue /= 27

        callChars[4] = CALLSIGN_CHARS[(remainingValue % 27) + 10]
        remainingValue /= 27

        callChars[3] = CALLSIGN_CHARS[remainingValue % 10]
        remainingValue /= 10

        callChars[2] = CALLSIGN_CHARS[remainingValue % 10]
        remainingValue /= 10

        callChars[1] = CALLSIGN_CHARS[remainingValue % 36]
        remainingValue /= 36

        callChars[0] = CALLSIGN_CHARS[remainingValue % 37]

        return callChars.joinToString("").trimEnd()
    }

    /**
     * Pack grid locator and power into a 22-bit integer value.
     *
     * @param grid Maidenhead grid locator (4 characters)
     * @param powerDbm Power level in dBm
     * @return Packed grid and power as 22-bit integer
     */
    fun packGridAndPower(grid: String, powerDbm: Int): Int
    {
        if (!isValidMaidenheadGrid(grid))
        {
            throw IllegalArgumentException("Invalid Maidenhead grid format: $grid")
        }

        var result = 0

        // Grid is 4 characters: two letters (A-R) and two digits (0-9)
        result = grid[0] - 'A'                      // First letter (0-17)
        result = result * 10 + (grid[1] - '0')      // First digit (0-9)
        result = result * 18 + (grid[2] - 'A')      // Second letter (0-17)
        result = result * 10 + (grid[3] - '0')      // Second digit (0-9)

        // Add power code
        val powerCode = powerDbmToPowerCode(powerDbm)
        result = result * 64 + powerCode

        return result
    }

    /**
     * Unpack grid locator and power from a 22-bit integer value.
     *
     * @param packedGridAndPower Packed grid and power value
     * @return Pair containing grid locator and power in dBm
     */
    fun unpackGridAndPower(packedGridAndPower: Int): Pair<String, Int>
    {
        var remainingValue = packedGridAndPower

        // Extract power code (6 bits, 0-63)
        val powerCode = remainingValue % 64
        remainingValue /= 64
        val powerDbm = powerCodeToPowerDbm(powerCode)

        // Extract grid locator (16 bits total)
        val grid = CharArray(4)

        grid[3] = '0' + (remainingValue % 10)       // Fourth character (digit)
        remainingValue /= 10

        grid[2] = 'A' + (remainingValue % 18)       // Third character (letter)
        remainingValue /= 18

        grid[1] = '0' + (remainingValue % 10)       // Second character (digit)
        remainingValue /= 10

        grid[0] = 'A' + remainingValue              // First character (letter)

        return Pair(grid.joinToString(""), powerDbm)
    }

    /**
     * Apply convolutional encoding to message bits.
     *
     * @param messageBits Input message bits
     * @return Encoded bits (twice as many as input due to rate 1/2 code)
     */
    fun applyConvolutionalEncoding(messageBits: IntArray): IntArray
    {
        val encodedBits = IntArray(messageBits.size * 2)

        for (bitIndex in messageBits.indices)
        {
            var parity1 = 0
            var parity2 = 0

            // Apply each term of the polynomials
            for (polynomialIndex in 0 until 32)
            {
                val inputBitIndex = bitIndex - polynomialIndex
                val inputBit = if (inputBitIndex >= 0) messageBits[inputBitIndex] else 0

                if (CONVOLUTIONAL_POLY1[polynomialIndex] == 1)
                {
                    parity1 = parity1 xor inputBit
                }

                if (CONVOLUTIONAL_POLY2[polynomialIndex] == 1)
                {
                    parity2 = parity2 xor inputBit
                }
            }

            // Store the two parity bits
            encodedBits[bitIndex * 2] = parity1
            encodedBits[bitIndex * 2 + 1] = parity2
        }

        return encodedBits
    }

    /**
     * Interleave encoded bits using bit-reversal permutation.
     *
     * @param encodedBits Input encoded bits
     * @return Interleavened bits
     */
    fun interleaveBits(encodedBits: IntArray): IntArray
    {
        val interleavedBits = IntArray(encodedBits.size)

        for (bitIndex in encodedBits.indices)
        {
            val newIndex = BIT_REVERSAL_TABLE[bitIndex] % encodedBits.size
            interleavedBits[newIndex] = encodedBits[bitIndex]
        }

        return interleavedBits
    }

    /**
     * Deinterleave bits using bit-reversal permutation (reverse of interleaving).
     *
     * @param interleavedBits Input interleaved bits
     * @return Deinterleaved bits
     */
    fun deinterleaveBits(interleavedBits: IntArray): IntArray
    {
        val deinterleavedBits = IntArray(interleavedBits.size)

        for (bitIndex in interleavedBits.indices)
        {
            val originalIndex = BIT_REVERSAL_TABLE[bitIndex] % interleavedBits.size
            deinterleavedBits[bitIndex] = interleavedBits[originalIndex]
        }

        return deinterleavedBits
    }

    /**
     * Generate 4-FSK symbols from interleaved bits and sync vector.
     *
     * @param interleavedBits Input interleaved bits
     * @return Array of 4-FSK symbols (0-3)
     */
    fun generateSymbols(interleavedBits: IntArray): IntArray
    {
        val symbols = IntArray(SYMBOL_COUNT)

        for (symbolIndex in 0 until SYMBOL_COUNT)
        {
            // Get data bit and sync bit
            val dataBit = if (symbolIndex < interleavedBits.size) interleavedBits[symbolIndex] else 0
            val syncBit = SYNC_VECTOR[symbolIndex]

            // Combine to create a 4-FSK symbol (0-3)
            // MSB = data bit, LSB = sync bit
            symbols[symbolIndex] = (dataBit shl 1) or syncBit
        }

        return symbols
    }

    /**
     * Handle special callsigns that contain '/' separators.
     * FIXME: This is a simplified implementation - full WSPR spec has complex rules for these.
     *
     * @param callsign Special callsign with '/' separator
     * @return Packed callsign value
     */
    private fun packSpecialCallsign(callsign: String): Int
    {
        // Simplified approach: remove '/' and pack main part
        val cleanCallsign = callsign.replace("/", "").take(6)
        return packCallsign(cleanCallsign)
    }
}