package org.operatorfoundation.audiowave.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

import timber.log.Timber

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
 * - Flow-based API for reactive programming
 *
 * @param T The type of elements stored in the buffer
 * @param capacity The fixed capacity of the buffer
 */
class CircularBuffer<T>(capacity: Int)
{
    private val buffer: Array<Any?> = arrayOfNulls(capacity)
    private var readPosition = 0
    private var writePosition = 0
    private var available = 0
    private val lock = ReentrantLock()

    /**
     * Write data to the buffer.
     *
     * This method adds elements to the circular buffer.
     * If there isn't enough space available, it will write as much as possible.
     *
     * @param data Array of elements to write
     * @param offset Starting position in the source array
     * @param length Number of elements to write
     * @return The actual number of elements written
     */
    fun write(data: Array<T>, offset: Int, length: Int): Int
    {
        return ErrorHandler.runCatching {
            if (length <= 0) return@runCatching 0

            lock.withLock {
                val spaceAvailable = buffer.size - available
                if (spaceAvailable <= 0) return@withLock 0

                val elementsToWrite = minOf(length, spaceAvailable)

                // Handle buffer wrap-around if necessary
                if (writePosition + elementsToWrite <= buffer.size)
                {
                    // Case 1: No wrap-around needed, single copy operation
                    System.arraycopy(data, offset, buffer, writePosition, elementsToWrite)
                    writePosition += elementsToWrite

                    // If we've reached the end exactly, reset to beginning
                    if (writePosition == buffer.size)
                    {
                        writePosition = 0
                    }
                }
                else
                {
                    // Case 2: Buffer wrap-around required, two copy operations
                    val firstPartSize = buffer.size - writePosition
                    System.arraycopy(data, offset, buffer, writePosition, firstPartSize)
                    System.arraycopy(data, offset + firstPartSize, buffer, 0, elementsToWrite - firstPartSize)
                    writePosition = elementsToWrite - firstPartSize
                }

                available += elementsToWrite
                elementsToWrite
            }
        }.getOrElse { error ->
            Timber.e("Error writing to buffer: ${ErrorHandler.getErrorMessage(error)}")
            0
        }
    }

    /**
     * Write a single element to the buffer.
     *
     * @param element The element to write
     * @return Result containing true if written successfully, or failure with error
     */
    fun write(element: T): Result<Boolean>
    {
        return ErrorHandler.runCatching {
            lock.withLock {
                if (available >= buffer.size) return@withLock false

                buffer[writePosition] = element
                writePosition++
                if (writePosition >= buffer.size)
                {
                    writePosition = 0
                }

                available++
                true
            }
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
    fun read(data: Array<T>, offset: Int, length: Int): Int
    {
        return ErrorHandler.runCatching {
            if (length <= 0) return@runCatching 0

            lock.withLock {
                if (available <= 0) return@withLock 0

                val elementsToRead = minOf(length, available)

                // Handle buffer wrap-around if necessary
                if (readPosition + elementsToRead <= buffer.size)
                {
                    // Case 1: No wrap-around needed, single copy operation
                    System.arraycopy(buffer, readPosition, data, offset, elementsToRead)
                    readPosition += elementsToRead

                    // If we've reached the end exactly, reset to beginning
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
                    System.arraycopy(buffer, 0, data, offset + firstPartSize, elementsToRead - firstPartSize)
                    readPosition = elementsToRead - firstPartSize
                }

                available -= elementsToRead
                elementsToRead
            }
        }.getOrElse { error ->
            Timber.e("Error reading from buffer: ${ErrorHandler.getErrorMessage(error)}")
            0
        }
    }

    /**
     * Read a single element from the buffer.
     *
     * @return Result containing the element, or failure if the buffer is empty or an error occurs
     */
    @Suppress("UNCHECKED_CAST")
    fun read(): Result<T>
    {
        return ErrorHandler.runCatching {
            lock.withLock {
                if (available <= 0) throw NoSuchElementException("Buffer is empty")

                val element = buffer[readPosition] as T
                buffer[readPosition] = null  // Help with garbage collection

                readPosition++
                if (readPosition >= buffer.size)
                {
                    readPosition = 0
                }

                available--
                element
            }
        }
    }

    /**
     * Creates a flow that emits elements from the buffer as they become available.
     * This is useful for reactive processing of buffer contents.
     *
     * @return Flow emitting elements from the buffer
     */
    fun asFlow(): Flow<T> = flow {
        while (true)
        {
            read().fold(
                onSuccess = { emit(it) },
                onFailure = { error ->
                    if (error !is NoSuchElementException)
                    {
                        throw error
                    }
                    // If buffer is empty, we just wait for more data
                    kotlinx.coroutines.delay(10)
                }
            )
        }
    }

    /**
     * Peek at the next element without removing it.
     *
     * @return Result containing the next element, or failure if the buffer is empty
     */
    @Suppress("UNCHECKED_CAST")
    fun peek(): Result<T>
    {
        return ErrorHandler.runCatching {
            lock.withLock {
                if (available <= 0) throw NoSuchElementException("Buffer is empty")
                buffer[readPosition] as T
            }
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
     *
     * @return Result indicating success or failure
     */
    fun clear(): Result<Unit> = ErrorHandler.runCatching {
        lock.withLock {
            // Clear references to help garbage collection
            for (i in buffer.indices) {
                buffer[i] = null
            }

            readPosition = 0
            writePosition = 0
            available = 0
        }
    }
}