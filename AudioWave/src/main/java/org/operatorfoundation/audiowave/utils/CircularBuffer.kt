package org.operatorfoundation.audiowave.utils

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A generic thread-safe circular buffer implementation.
 *
 * This class provides a type-safe circular buffer that can store any type of data.
 * It efficiently handles continuous streams of data without requiring frequent
 * memory reallocations and supports thread-safe concurrent access.
 *
 * Features:
 * - Generic type support
 * - Fixed-size circular buffer that reuses memory
 * - Thread-safe read and write operations
 * - Automatic buffer wrap-around handling
 * - Overflow protection
 *
 * @param T The type of elements stored in the buffer
 * @param capacity The fixed capacity of the buffer
 */
class CircularBuffer<T>(capacity: Int)
{
    // Ensure capacity is valid
    init {
        require(capacity > 0) { "Buffer capacity must be positive" }
    }

    private val buffer: Array<Any?> = arrayOfNulls(capacity)
    private var readPosition = 0
    private var writePosition = 0
    private var available = 0
    private val lock = ReentrantLock()

    /**
     * Write data to the buffer.
     *
     * This method adds elements to the circular buffer.
     * If there isn't enough room, it will write as much as possible.
     *
     * @param data Array of elements to write
     * @param offset Starting position in the source array
     * @param length Number of elements to write
     * @return The actual number of elements written
     */
    fun write(data: Array<T>, offset: Int = 0, length: Int = data.size - offset): Int
    {
        if (length <= 0 || offset < 0 || offset + length > data.size) return 0

        return lock.withLock {
            val spaceAvailable = buffer.size - available
            if (spaceAvailable <= 0) return@withLock 0

            val elementsToWrite = minOf(length, spaceAvailable)

            // Handle buffer wrap-around if necessary
            if (writePosition + elementsToWrite <= buffer.size)
            {
                // Case 1: No wrap-around needed, single copy operation
                for (i in 0 until elementsToWrite)
                {
                    // Clear old value first
                    buffer[writePosition + i] = null
                    buffer[writePosition + i] = data[offset + i]
                }
                writePosition += elementsToWrite

                // If we've reached the end exactly, reset to the beginning
                if (writePosition == buffer.size)
                {
                    writePosition = 0
                }
            }
            else
            {
                // Case 2: Buffer wrap-around required, two copy operations
                val firstPartSize = buffer.size - writePosition

                // First part: copy to the end of buffer
                for (i in 0 until firstPartSize)
                {
                    buffer[writePosition + i] = null
                    buffer[writePosition + i] = data[offset + i]
                }

                // Second part: copy from the beginning of the buffer
                for (i in 0 until elementsToWrite - firstPartSize)
                {
                    buffer[i] = null
                    buffer[i] = data[offset + firstPartSize + i]
                }

                writePosition = elementsToWrite - firstPartSize
            }

            available += elementsToWrite
            elementsToWrite
        }
    }

    /**
     * Write a single element to the buffer.
     *
     * @param element The element to write
     * @return True if the element was written successfully, false if the buffer is full
     */
    fun write(element: T): Boolean
    {
        return lock.withLock {
            if (available >= buffer.size) return@withLock false

            buffer[writePosition] = element
            writePosition ++
            if (writePosition == buffer.size) { writePosition = 0}

            available ++
            true
        }
    }

    /**
     * Read data from the buffer.
     *
     * This method copies elements from the circular buffer into the provided array.
     * If there aren't enough elements available, it will read as much as possible.
     *
     * @param data Destination array where elements will be copied
     * @param offset Starting position in the destination array
     * @param length Maximum number of elements to read
     * @return The actual number of elements read
     */
    @Suppress("UNCHECKED_CAST")
    fun read(data: Array<T>, offset: Int = 0, length: Int = data.size - offset): Int
    {
        if (length <= 0 || offset < 0 || offset + length > data.size) return 0

        return lock.withLock {
            if (available <= 0) return 0

            val elementsToRead = minOf(length, available)

            // Handle buffer wrap-around if necessary
            if (readPosition + elementsToRead <= buffer.size)
            {
                // Case 1: No wrap-around needed, single copy operation
                for (i in 0 until elementsToRead)
                {
                    data[offset + i] = buffer[readPosition + i] as T
                    buffer[readPosition + i] = null
                }
                readPosition += elementsToRead

                // If we've reached the end exactly, reset to beginning
                if (readPosition == buffer.size) {
                    readPosition = 0
                }
            }
            else
            {
                // Case 2: Buffer wrap-around required, two copy operations
                val firstPartSize = buffer.size - readPosition

                // First part: copy from current read position to end of buffer
                for (i in 0 until firstPartSize) {
                    data[offset + i] = buffer[readPosition + i] as T
                    buffer[readPosition + i] = null // Help with garbage collection
                }

                // Second part: copy from beginning of buffer
                for (i in 0 until elementsToRead - firstPartSize) {
                    data[offset + firstPartSize + i] = buffer[i] as T
                    buffer[i] = null // Help with garbage collection
                }

                readPosition = elementsToRead - firstPartSize
            }
            available -= elementsToRead
            elementsToRead
        }
    }

    /**
     * Read a single element from the buffer.
     *
     * @return The element, or null if the buffer is empty
     */
    @Suppress("UNCHECKED_CAST")
    fun read(): T? {
        return lock.withLock {
            if (available <= 0) return@withLock null

            val element = buffer[readPosition] as T
            buffer[readPosition] = null // Help with garbage collection

            readPosition++
            if (readPosition >= buffer.size) {
                readPosition = 0
            }

            available--
            element
        }
    }

    /**
     * Peek at the next element without removing it.
     *
     * @return The next element, or null if the buffer is empty
     */
    @Suppress("UNCHECKED_CAST")
    fun peek(): T? {
        return lock.withLock {
            if (available <= 0) return@withLock null
            return@withLock buffer[readPosition] as T
        }
    }

    /**
     * Peek at multiple elements without removing them.
     *
     * @param maxElements Maximum number of elements to peek
     * @return List of elements, may be smaller than maxElements if fewer are available
     */
    @Suppress("UNCHECKED_CAST")
    fun peekMultiple(maxElements: Int): List<T> {
        return lock.withLock {
            if (available <= 0) return@withLock emptyList()

            val elementsToRead = minOf(maxElements, available)
            val result = ArrayList<T>(elementsToRead)

            if (readPosition + elementsToRead <= buffer.size) {
                // No wrap-around
                for (i in 0 until elementsToRead) {
                    result.add(buffer[readPosition + i] as T)
                }
            } else {
                // Wrap-around
                val firstPartSize = buffer.size - readPosition
                for (i in 0 until firstPartSize) {
                    result.add(buffer[readPosition + i] as T)
                }
                for (i in 0 until elementsToRead - firstPartSize) {
                    result.add(buffer[i] as T)
                }
            }

            result
        }
    }

    /**
     * Get the number of elements available to read.
     *
     * @return The number of elements that can be read from the buffer
     */
    fun available(): Int = lock.withLock { available }

    /**
     * Check if the buffer is empty.
     *
     * @return true if the buffer contains no elements, false otherwise
     */
    fun isEmpty(): Boolean = lock.withLock { available == 0 }

    /**
     * Check if the buffer is full.
     *
     * @return true if the buffer is at capacity, false otherwise
     */
    fun isFull(): Boolean = lock.withLock { available == buffer.size }

    /**
     * Get the total capacity of the buffer.
     *
     * @return The maximum number of elements the buffer can hold
     */
    fun capacity(): Int = buffer.size

    /**
     * Clear the buffer.
     *
     * This resets the read and write positions, making the buffer empty.
     */
    fun clear() = lock.withLock {
        // Clear references to help garbage collection
        for (i in buffer.indices) {
            buffer[i] = null
        }

        readPosition = 0
        writePosition = 0
        available = 0
    }

    /**
     * Get the amount of free space in the buffer.
     *
     * @return Number of elements that can be written before the buffer is full
     */
    fun freeSpace(): Int = lock.withLock { buffer.size - available }

    /**
     * Process multiple elements in the buffer without copying them to another array.
     *
     * This method provides efficient, in-place access to the buffer's contents through
     * the BufferAccess interface. This is particularly useful for operations that need
     * to analyze or transform data without allocating additional memory, which is crucial
     * for performance-critical Android applications like audio/video processing or real-time
     * data analysis.
     *
     * The processor function receives a BufferAccess object that provides safe, convenient
     * access to the buffer elements through various methods (get(), asList(), etc.).
     *
     * Usage example:
     * ```
     * // Calculate average of values without copying data
     * val avg = buffer.processInPlace(buffer.available()) { access ->
     *     val sum = access.asList().sumBy { it as Int }
     *     println("Average: ${sum.toFloat() / access.availableCount}")
     *     access.availableCount  // Process all elements
     * }
     * ```
     *
     * @param maxElements Maximum number of elements the processor should handle
     * @param processor Function that processes the elements and returns how many were processed.
     *                 The processor MUST return a number between 0 and maxElements.
     * @return Number of elements successfully processed and removed from the buffer
     * @throws IllegalStateException if processor returns more elements than were available
     */
    @Suppress("UNCHECKED_CAST")
    fun processInPlace(maxElements: Int, processor: (BufferAccess<T>) -> Int): Int {
        return lock.withLock {
            if (available <= 0) return@withLock 0

            val elementsToProcess = minOf(maxElements, available)
            val access = BufferAccessImpl(buffer as Array<T?>, readPosition, writePosition, buffer.size, elementsToProcess)

            val processed = processor(access)
            if (processed <= 0) return@withLock 0

            // Update positions based on what was actually processed
            if (processed > elementsToProcess) {
                throw IllegalStateException("Processor reported more elements processed than were available")
            }

            // Mark elements as read
            if (readPosition + processed <= buffer.size) {
                // No wrap
                for (i in 0 until processed) {
                    buffer[readPosition + i] = null
                }

                readPosition += processed
                if (readPosition == buffer.size) {
                    readPosition = 0
                }
            } else {
                // Wrap around
                val firstPartSize = buffer.size - readPosition

                for (i in 0 until firstPartSize) {
                    buffer[readPosition + i] = null
                }

                val secondPartSize = processed - firstPartSize
                for (i in 0 until secondPartSize) {
                    buffer[i] = null
                }

                readPosition = secondPartSize
            }

            available -= processed
            processed
        }
    }

    /**
     * Interface for safely accessing buffer contents during in-place processing.
     *
     * This interface provides multiple ways to access the data in the circular buffer
     * without copying it to another array. It handles all the complexity of buffer
     * wrap-around internally, so the client code can focus on processing the data.
     *
     * Key features:
     * - Random access to elements via get()
     * - List view of all available elements via asList()
     * - Segment-based access for optimized processing of wrapped data
     * - Information about buffer state (available elements, wrap status)
     */
    interface BufferAccess<T> {
        /**
         * Get an element at the specified relative position in the buffer.
         *
         * The position is relative to the current read position, so:
         * - position 0 is the first available element
         * - position (availableCount-1) is the last available element
         *
         * @param relativePosition Position relative to the current read position (0-based)
         * @return The element at the specified position, or null if position is invalid
         */
        fun get(relativePosition: Int): T?

        /**
         * Get all available elements as a list.
         *
         * This creates a new list containing copies of references to all the elements
         * in the buffer that are available for processing. The list is in the correct
         * logical order, handling any buffer wrap-around internally.
         *
         * Note: This allocates a new list, so it's not as memory-efficient as using
         * firstSegment/secondSegment for large buffers.
         *
         * @return List of all available elements
         */
        fun asList(): List<T>

        /**
         * Total number of elements available for processing.
         *
         * This is the maximum number of elements that can be safely accessed
         * through this BufferAccess instance.
         */
        val availableCount: Int

        /**
         * Whether the available data wraps around the buffer.
         *
         * When true, the data is stored in two non-contiguous segments:
         * from readPosition to the end of the buffer, and then from the
         * beginning of the buffer. You can access these segments separately
         * using firstSegment and secondSegment properties.
         */
        val isWrapped: Boolean

        /**
         * Get the first segment of available data.
         *
         * If the buffer is wrapped (isWrapped is true), this returns the elements
         * from the current read position to the end of the buffer.
         * If the buffer is not wrapped, this returns all available elements.
         *
         * This is useful for efficient batch processing when you need to process
         * contiguous blocks of memory.
         */
        val firstSegment: List<T>

        /**
         * Get the second segment of available data.
         *
         * If the buffer is wrapped (isWrapped is true), this returns the elements
         * from the beginning of the buffer up to the write position.
         * If the buffer is not wrapped, this returns an empty list.
         *
         * This is useful for efficient batch processing when you need to process
         * contiguous blocks of memory.
         */
        val secondSegment: List<T>
    }

    /**
     * Implementation of BufferAccess interface that provides safe access to the
     * underlying buffer elements during in-place processing.
     *
     * This class handles all the complexity of circular buffer access, including
     * wrap-around logic, boundary checking, and type casting.
     */
    private inner class BufferAccessImpl(
        private val typedBuffer: Array<T?>,
        private val readPos: Int,
        private val writePos: Int,
        private val bufferSize: Int,
        override val availableCount: Int
    ) : BufferAccess<T> {

        /**
         * Determines if the available data wraps around the buffer boundary.
         * When true, the data is in two non-contiguous segments.
         */
        override val isWrapped: Boolean = readPos + availableCount > bufferSize

        /**
         * Get an element at the specified relative position.
         * Handles buffer wrap-around automatically and performs bounds checking.
         *
         * @param relativePosition Position relative to current read position (0-based)
         * @return The element, or null if position is invalid
         */
        override fun get(relativePosition: Int): T? {
            if (relativePosition < 0 || relativePosition >= availableCount) {
                return null
            }

            // Calculate the actual position in the underlying array,
            // handling wrap-around if necessary
            val actualPos = if (readPos + relativePosition < bufferSize) {
                readPos + relativePosition
            } else {
                (readPos + relativePosition) - bufferSize
            }

            return typedBuffer[actualPos]
        }

        /**
         * Get all available elements as a list in the correct logical order.
         * This method handles buffer wrap-around internally.
         *
         * @return List containing all available elements
         */
        override fun asList(): List<T> {
            val result = ArrayList<T>(availableCount)
            for (i in 0 until availableCount) {
                get(i)?.let { result.add(it) }
            }
            return result
        }

        /**
         * Get the first contiguous segment of available data.
         * For efficient batch processing of elements.
         *
         * @return List of elements in the first segment
         */
        override val firstSegment: List<T>
            get() {
                val segmentSize = if (isWrapped) bufferSize - readPos else availableCount
                val result = ArrayList<T>(segmentSize)
                for (i in 0 until segmentSize) {
                    typedBuffer[readPos + i]?.let { result.add(it) }
                }
                return result
            }

        /**
         * Get the second contiguous segment of available data.
         * Empty if buffer doesn't wrap around.
         *
         * @return List of elements in the second segment, or empty list if not wrapped
         */
        override val secondSegment: List<T>
            get() {
                if (!isWrapped) return emptyList()

                val segmentSize = availableCount - (bufferSize - readPos)
                val result = ArrayList<T>(segmentSize)
                for (i in 0 until segmentSize) {
                    typedBuffer[i]?.let { result.add(it) }
                }
                return result
            }
    }


}
