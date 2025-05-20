package org.operatorfoundation.audiowave.utils

import timber.log.Timber
import kotlin.math.sqrt
import kotlin.math.abs
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility class providing common audio format conversion and manipulation functions.
 *
 * This class contains static methods for common audio operations such as:
 * - Converting between different audio data formats (bytes to samples)
 * - Calculating audio metrics (RMS level, peak level, etc.)
 * - Audio buffer manipulation functions
 * - Value range limiting functions
 *
 * These utilities are designed to work with standard PCM audio formats,
 * with a focus on 16-bit audio which is common in Android audio applications.
 */
object AudioUtils
{

    /**
     * Convert raw PCM byte data to 16-bit samples.
     *
     * This method efficiently converts a byte array containing 16-bit PCM data
     * into an array of short values, handling endianness properly.
     *
     * @param bytes Raw PCM byte data
     * @param byteOrder The byte order (endianness) of the PCM data, defaults to little-endian
     * @return Array of 16-bit samples as shorts
     */
    fun bytesToShorts(bytes: ByteArray, byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): ShortArray
    {
        return ErrorHandler.runCatching {
            if (bytes.isEmpty()) return@runCatching ShortArray(0)

            // Calculate number of samples (2 bytes per sample)
            val sampleCount = bytes.size / 2
            val samples = ShortArray(sampleCount)

            // Use ByteBuffer for efficient conversion with proper endianness
            val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
            val shortBuffer = buffer.asShortBuffer()
            shortBuffer.get(samples)

            samples
        }.getOrElse { error ->
            Timber.e("Error converting bytes to shorts: ${ErrorHandler.getErrorMessage(error)}")
            ShortArray(0)
        }
    }

    /**
     * Convert 16-bit samples to raw PCM byte data.
     *
     * @param samples Array of 16-bit samples as shorts
     * @param byteOrder The byte order (endianness) for the output bytes, defaults to little-endian
     * @return Raw PCM byte data
     */
    fun shortsToBytes(samples: ShortArray, byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteArray
    {
        return ErrorHandler.runCatching {
            if (samples.isEmpty()) return@runCatching ByteArray(0)

            // Allocate byte array (2 bytes per sample)
            val bytes = ByteArray(samples.size * 2)

            // Use ByteBuffer for efficient conversion with proper endianness
            val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
            val shortBuffer = buffer.asShortBuffer()
            shortBuffer.put(samples)

            bytes
        }.getOrElse { error ->
            Timber.e("Error converting shorts to bytes: ${ErrorHandler.getErrorMessage(error)}")
            ByteArray(0)
        }
    }

    /**
     * Extract a 16-bit sample from two bytes at the specified position.
     *
     * This is useful when you need to access individual samples without
     * converting the entire byte array.
     *
     * @param data Byte array containing PCM data
     * @param position Starting position of the sample in the byte array
     * @param byteOrder The byte order (endianness) of the data, defaults to little-endian
     * @return The 16-bit sample as a short
     * @throws IndexOutOfBoundsException if position or position+1 is out of bounds
     */
    fun getSampleAt(data: ByteArray, position: Int, byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): Short
    {
        return ErrorHandler.runCatching {
            // Ensure we have at least 2 bytes to read
            if (position + 1 >= data.size)
            {
                throw IndexOutOfBoundsException("Cannot read sample at position $position, array length is ${data.size}")
            }

            // Extract the sample based on byte order
            if (byteOrder == ByteOrder.LITTLE_ENDIAN)
            {
                // Little-endian: first byte is low byte, second byte is high byte
                ((data[position + 1].toInt() shl 8) or (data[position].toInt() and 0xFF)).toShort()
            }
            else
            {
                // Big-endian: first byte is high byte, second byte is low byte
                ((data[position].toInt() shl 8) or (data[position + 1].toInt() and 0xFF)).toShort()
            }

        }.getOrElse { error ->
            Timber.e("Error getting sample at position $position: ${ErrorHandler.getErrorMessage(error)}")
            0
        }
    }

    /**
     * Calculate the Root Mean Square (RMS) level from raw PCM byte data.
     *
     * RMS is a good measure of audio level as it accounts for both positive
     * and negative sample values and correlates well with perceived loudness.
     *
     * @param data Raw PCM byte data (assumed to be 16-bit, little-endian)
     * @param byteOrder The byte order (endianness) of the data, defaults to little-endian
     * @return RMS level normalized to range 0.0-1.0
     */
    fun calculateRmsLevel(data: ByteArray, byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): Float
    {
        return ErrorHandler.runCatching {
            if (data.size < 2) return@runCatching 0.0f

            var sum = 0.0
            var count = 0

            // Process 16-bit samples (2 bytes per sample)
            val sampleCount = data.size / 2
            for (sampleIndex in 0 until sampleCount)
            {
                val position = sampleIndex * 2

                // Get sample value using helper method
                val sample = getSampleAt(data, position, byteOrder)

                // Square the sample and add to sum
                sum += sample * sample
                count++
            }

            if (count == 0) return@runCatching 0.0f

            val rms = Math.sqrt(sum / count)
            // Normalize to 0.0-1.0 range (16-bit audio has range -32768 to 32767)
            (rms / 32768.0).toFloat()
        }.getOrElse { error ->
            Timber.e("Error calculating RMS level: ${ErrorHandler.getErrorMessage(error)}")
            0.0f
        }
    }

    /**
     * Calculate the peak level (maximum absolute amplitude) from raw PCM byte data.
     *
     * @param data Raw PCM byte data (assumed to be 16-bit)
     * @param byteOrder The byte order (endianness) of the data, defaults to little-endian
     * @return Peak level normalized to range 0.0-1.0
     */
    fun calculatePeakLevel(data: ByteArray, byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): Float
    {
        return ErrorHandler.runCatching {
            if (data.size < 2) return@runCatching 0.0f

            var maxAbs = 0

            // Process 16-bit samples (2 bytes per sample)
            val sampleCount = data.size / 2
            for (sampleIndex in 0 until sampleCount)
            {
                val position = sampleIndex * 2

                // Get sample value using helper method
                val sample = getSampleAt(data, position, byteOrder)

                // Track maximum absolute value
                val absValue = Math.abs(sample.toInt())
                if (absValue > maxAbs) {
                    maxAbs = absValue
                }
            }

            // Normalize to 0.0-1.0 range
            (maxAbs / 32768.0).toFloat()
        }.getOrElse { error ->
            Timber.e("Error calculating peak level: ${ErrorHandler.getErrorMessage(error)}")
            0.0f
        }
    }

    /**
     * Clip a float value to fit within Short range and convert to Short.
     *
     * @param value The float value to clip
     * @return The value as a Short, clipped to the Short range
     */
    fun clipToShort(value: Float): Short
    {
        return when
        {
            value > Short.MAX_VALUE -> Short.MAX_VALUE
            value < Short.MIN_VALUE -> Short.MIN_VALUE
            else -> value.toInt().toShort()
        }
    }

    /**
     * Limit a float value to a specified range.
     *
     * @param value The value to limit
     * @param min The minimum allowed value
     * @param max The maximum allowed value
     * @return The limited value
     */
    fun limit(value: Float, min: Float, max: Float): Float
    {
        return when
        {
            value > max -> max
            value < min -> min
            else -> value
        }
    }
}