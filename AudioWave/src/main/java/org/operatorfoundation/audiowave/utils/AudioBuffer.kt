package org.operatorfoundation.audiowave.utils

import android.health.connect.datatypes.units.Length
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A thread-safe circular buffer specifically designed for audio data processing.
 *
 * This class implements a circular (ring) buffer that efficiently handles continuous
 * streams of audio data without requiring frequent memory reallocations.
 * It provides thread-safe read and write operations,
 * making it suitable for concurrent audio processing scenarios where one thread writes to the buffer
 * and another reads from it.
 *
 * Features:
 * - Fixed-size circular buffer that reuses memory
 * - Thread-safe read and write operations
 * - Automatic buffer wrap-around handling
 * - Overflow protection (write operations will fail rather than overwrite unread data)
 *
 * Example usage:
 * ```
 * // Create a 10k buffer
 * val buffer = AudioBuffer(10240)
 *
 * // In a producer thread
 * val bytesWritten = buffer.write(audioData, 0, audioData.size)
 *
 * // In a consumer thread
 * val readBuffer = ByteArray(1024)
 * val bytesRead = buffer.read(readBuffer, 0, readBuffer.size)
 * ```
 *
 * Note: This implementation prioritizes thread safety and correctness over maximum performance.
 * For extremely high-performance applications, a lock-free implementation might be preferable.
 *
 * @param capacity The fixed capacity of the buffer in bytes
 */
class AudioBuffer(capacity: Int)
{
    /** The internal byte array that stores the audio data */
    private val buffer = ByteArray(capacity)

    /** Current read position within the buffer */
    private var readPosition = 0

    /** Current write position within the buffer */
    private var writePosition = 0

    /** Number of bytes available to read */
    private var available = 0

    /** Lock for thread synchronization */
    private val lock = ReentrantLock()

    /**
     * Write data to the buffer.
     *
     * This method copies data from the provided byte array into the circular buffer.
     * If there isn't enough space available, it will write as much as possible.
     *
     * @param data Source array containing the data to write
     * @param offset Starting position in the source array
     * @param length Number of bytes to write from the source array
     * @return The actual number of bytes written, which may be less than requested if the buffer is full
     */
    fun write(data: ByteArray, offset: Int, length: Int): Int
    {
        if (length <= 0) return 0

        return lock.withLock {
            val spaceAvailable = buffer.size - available
            if (spaceAvailable <= 0) return@withLock 0

            val bytesToWrite = minOf(length, spaceAvailable)

            // Handle buffer wrap-around if necessary
            if (writePosition + bytesToWrite <= buffer.size)
            {
                // Case 1: No wrap-around needed, single copy operation
                System.arraycopy(data, offset, buffer, writePosition, bytesToWrite)
                writePosition += bytesToWrite

                // If we've reached the end exactly, reset to beginning
                if (writePosition == buffer.size) {
                    writePosition = 0
                }
            }
            else
            {
                // Case 2: Buffer wrap-around required, two copy operations
                val firstPartSize = buffer.size - writePosition
                System.arraycopy(data, offset, buffer, writePosition, firstPartSize)
                System.arraycopy(data, offset + firstPartSize, buffer, 0, bytesToWrite - firstPartSize)
                writePosition = bytesToWrite - firstPartSize
            }

            available += bytesToWrite
            bytesToWrite
        }
    }

    /**
     * Read data from the buffer
     *
     * This method copies data from the circular buffer into the provided byte array.
     * If there aren't enough bytes available, it will read as much as possible.
     *
     * @param data Destination array where data will be copied
     * @param offset Starting position in the destination array
     * @param length Maximum number of bytes to read
     * @return The actual number of bytes read, which may be less than requested if the buffer has insufficient data
     */
    fun read(data: ByteArray, offset: Int, length: Int): Int
    {
        if (length <= 0) return 0

        return lock.withLock {
            if (available <= 0) return@withLock 0

            val bytesToRead = minOf(length, available)

            // Handle buffer wrap-around if necessary
            if (readPosition + bytesToRead <= buffer.size )
            {
                // Case 1: No wrap-around needed, single copy operation
                System.arraycopy(buffer, readPosition, data, offset, bytesToRead)
                readPosition += bytesToRead

                // If we've reached the end exactly, reset to the beginning
                if (readPosition == buffer.size)
                {
                    readPosition = 0
                }
            }
            else
            {
                // Case 2: Buffer wrap-around required, two copy operations
                val firstPartSize = buffer.size - readPosition
                System.arraycopy(buffer, readPosition, data, offset, firstPartSize)
                System.arraycopy(buffer, 0, data, offset + firstPartSize, bytesToRead - firstPartSize)
                readPosition = bytesToRead - firstPartSize
            }

            available -= bytesToRead
            bytesToRead
        }
    }

    /**
     * Get the number of bytes available to read.
     *
     * @return The number of bytes that can be read from the buffer
     */
    fun available(): Int = available

    /**
     * Check if the buffer is empty.
     *
     * @return true if the buffer contains no data, false if it does
     */
    fun isEmpty(): Boolean = available == 0

    /**
     * Check if the buffer is full.
     *
     * @return true if the buffer is at capacity, false if not
     */
    fun isFull(): Boolean = available == buffer.size

    /**
     * Get the total capacity of the buffer.
     *
     * @return The maximum number of bytes the buffer can hold.
     */
    fun capacity(): Int = buffer.size

    /**
     * Clear the buffer.
     *
     * This resets the read and write positions, effectively making the buffer empty.
     * The underlying byte array is not zeroed out for performance reasons.
     */
    fun clear() = lock.withLock {
        readPosition = 0
        writePosition = 0
        available = 0
    }

}