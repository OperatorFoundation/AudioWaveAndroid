package org.operatorfoundation.audiowave.threading

import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages background threads for audio processing tasks.
 *
 * This class provides a standardized way to execute audio processing tasks
 * on background threads, ensuring proper thread management and cleanup.
 *
 * Features:
 * - Thread pooling for efficient resource usage
 * - Proper thread naming for easier debugging
 * - Graceful shutdown with timeout
 * - Safe task execution with error handling
 *
 * Example usage:
 * ```
 * val threadManager = AudioThreadManager()
 *
 * // Execute a task
 * threadManager.executeTask {
 *      // Long running audio processing
 * }
 *
 * // When done with the manager
 * threadManager.shutdown()
 * ```
 */
class AudioThreadManager
{
    companion object
    {
        private const val DEFAULT_THREAD_COUNT = 2
        private const val SHUTDOWN_TIMEOUT_SECONDS = 2L
    }

    private val threadCounter = AtomicInteger(0)
    private val executorService: ScheduledExecutorService

    /**
     * Create a new AudioThreadManager with a specified number of threads.
     *
     * @param threadCount The number of threads in the thread pool
     */
    constructor(threadCount: Int = DEFAULT_THREAD_COUNT)
    {
        executorService = Executors.newScheduledThreadPool(threadCount) { runnable ->
            Thread(runnable).apply {
                name = "AudioWave-Worker-${threadCounter.incrementAndGet()}"
                priority = Thread.NORM_PRIORITY
                isDaemon = true
            }
        }
        Timber.d("AudioThreadManager initialized with $threadCount threads")
    }

    /**
     * Execute a task on a background thread.
     *
     * @param task The task to execute
     */
    fun executeTask(task: () -> Unit)
    {
        executorService.execute {
            try {
                task()
            } catch (error: Exception) {
                Timber.e(error, "Error executing audio task")
            }
        }
    }

    /**
     * Schedule a task to run after a delay.
     *
     * @param task The task to execute
     * @param delayMs The delay in milliseconds before execution
     */
    fun scheduleTask(task: () -> Unit, delayMs: Long) {
        executorService.schedule({
            try {
                task()
            } catch (e: Exception) {
                Timber.e(e, "Error executing scheduled audio task")
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Schedule a task to run repeatedly with a fixed delay between executions.
     *
     * @param task The task to execute
     * @param initialDelayMs The initial delay before the first execution
     * @param periodMs The period between executions
     */
    fun scheduleRepeatingTask(task: () -> Unit, initialDelayMs: Long, periodMs: Long) {
        executorService.scheduleWithFixedDelay({
            try {
                task()
            } catch (e: Exception) {
                Timber.e(e, "Error executing repeating audio task")
            }
        }, initialDelayMs, periodMs, TimeUnit.MILLISECONDS)
    }

    /**
     * Check if the thread manager has been shut down.
     *
     * @return True if the thread manager has been shut down, false otherwise
     */
    fun isShutdown(): Boolean {
        return executorService.isShutdown
    }

    /**
     * Shut down the thread manager, attempting to gracefully terminate all threads.
     *
     * This method will wait for all executing tasks to complete for up to
     * SHUTDOWN_TIMEOUT_SECONDS seconds before forcibly terminating them.
     */
    fun shutdown() {
        Timber.d("Shutting down AudioThreadManager")
        executorService.shutdown()

        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Timber.w("Some audio tasks did not terminate within timeout, forcing shutdown")
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            Timber.w(e, "Audio thread shutdown interrupted, forcing immediate shutdown")
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }


}