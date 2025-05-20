package org.operatorfoundation.audiowave.threading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.operatorfoundation.audiowave.utils.ErrorHandler
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages background execution for audio processing tasks.
 *
 * This class provides standardized ways to execute audio processing tasks
 * using both traditional thread pools and coroutines.
 *
 * Features:
 * - Thread pooling for efficient resource usage
 * - Proper thread naming for easier debugging
 * - Graceful shutdown with timeout
 * - Safe task execution with error handling
 * - Support for both thread-based and coroutine-based approaches
 *
 * Example usage with traditional threads:
 * ```
 * val threadManager = AudioThreadManager()
 *
 * // Execute a task
 * threadManager.executeTask {
 *     // Long-running audio processing
 * }
 *
 * // When done with the manager
 * threadManager.shutdown()
 * ```
 *
 * Example usage with coroutines:
 * ```
 * val threadManager = AudioThreadManager()
 *
 * // Launch a coroutine
 * threadManager.launchAudioTask {
 *     // Suspending audio processing
 * }
 *
 * // Create a repeating flow for continuous processing
 * threadManager.createRepeatingFlow(intervalMs = 100) {
 *    // Task to run every 100ms
 * }.collect { result ->
 *    // Process result
 * }
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

    // Coroutine scope for coroutine-based tasks
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
            ErrorHandler.runCatching {
                task()
            }.onFailure { error ->
                Timber.e("Error executing audio task: ${ErrorHandler.getErrorMessage(error)}")
            }
        }
    }

    /**
     * Launch a task using coroutines.
     *
     * @param task The suspending task to execute
     * @return Job that can be used to cancel the task
     */
    fun launchAudioTask(task: suspend () -> Unit): Job {
        return coroutineScope.launch {
            ErrorHandler.runCatching {
                task()
            }.onFailure { error ->
                Timber.e("Error in coroutine audio task: ${ErrorHandler.getErrorMessage(error)}")
            }
        }
    }

    /**
     * Create a flow that executes a task repeatedly at specified intervals.
     *
     * @param intervalMs The interval between executions in milliseconds
     * @param task The task to execute repeatedly
     * @return Flow emitting the results of each execution
     */
    fun <T> createRepeatingFlow(intervalMs: Long, task: suspend () -> T): Flow<T> = flow {
        while (true) {
            ErrorHandler.runCatching {
                emit(task())
            }.onFailure { error ->
                Timber.e("Error in repeating flow task: ${ErrorHandler.getErrorMessage(error)}")
                throw error  // Re-throw to be caught by downstream operators
            }
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Schedule a task to run after a delay.
     *
     * @param task The task to execute
     * @param delayMs The delay in milliseconds before execution
     */
    fun scheduleTask(task: () -> Unit, delayMs: Long) {
        executorService.schedule({
            ErrorHandler.runCatching {
                task()
            }.onFailure { error ->
                Timber.e("Error executing scheduled audio task: ${ErrorHandler.getErrorMessage(error)}")
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
            ErrorHandler.runCatching {
                task()
            }.onFailure { error ->
                Timber.e("Error executing repeating audio task: ${ErrorHandler.getErrorMessage(error)}")
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
     *
     * @return Result indicating success or failure
     */
    suspend fun shutdown(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext ErrorHandler.runCatching {
            Timber.d("Shutting down AudioThreadManager")

            // Cancel all coroutines
            coroutineScope.cancel()

            // Shutdown thread pool
            executorService.shutdown()

            try {
                if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    Timber.w("Some audio tasks did not terminate within timeout, forcing shutdown")
                    executorService.shutdownNow()
                }
            } catch (e: InterruptedException) {
                Timber.w("Audio thread shutdown interrupted, forcing immediate shutdown")
                executorService.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

}