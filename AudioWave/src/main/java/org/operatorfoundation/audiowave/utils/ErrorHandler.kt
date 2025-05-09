package org.operatorfoundation.audiowave.utils
import org.operatorfoundation.audiowave.exception.AudioException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * Centralized error handling utility for the AudioWave library.
 *
 * This class provides consistent error handling patterns for various exception types,
 * ensuring errors are properly logged and handled throughout the library.
 *
 * Example usage with Result:
 * ```
 * val result = ErrorHandler.runCatching { device.connect() }
 * result.fold(
 *     onSuccess = { /* Handle success */ },
 *     onFailure = { error ->
 *         // Handle the error, perhaps show UI notification
 *         showErrorToast(ErrorHandler.getErrorMessage(error))
 *     }
 * )
 * ```
 *
 * Example usage with Flow:
 * ```
 * audioProcessor.processFlow()
 *     .catch { error ->
 *         // Handle the error
 *         showErrorToast(ErrorHandler.getErrorMessage(error))
 *     }
 *     .collect { /* Process results */ }
 * ```
 */
object ErrorHandler
{
    /**
     * Get a user-friendly error message for a given exception.
     *
     * @param exception The exception to handle
     * @return A user-friendly error message
     */
    fun getErrorMessage(exception: Throwable): String
    {
        return when (exception)
        {
            is AudioException.DeviceConnectionException ->
                "Could not connect to audio device: ${exception.message}"

            is AudioException.PermissionDeniedException ->
                "Permission denied to access audio device"

            is AudioException.AudioProcessingException ->
                "Error processing audio: ${exception.message}"

            is AudioException.UnsupportedFormatException ->
                "Unsupported audio format: ${exception.message}"

            is AudioException.NoDeviceConnectedException ->
                "No audio device connected"

            is AudioException.ResourceUnavailableException ->
                "Required resource unavailable: ${exception.message}"

            else ->
                "An unexpected error occurred: ${exception.message}"
        }
    }

    /**
     * Handle an exception by logging it appropriately and returning a user-friendly message.
     *
     * @param exception The exception to handle
     * @return Triple containing: user-friendly message, whether exception is recognized, and the logged exception
     */
    fun handleException(exception: Throwable): Triple<String, Boolean, Throwable>
    {
        val isRecognized = when (exception)
        {
            is AudioException.DeviceConnectionException -> {
                Timber.e(exception, "USB connection error: %s", exception.message)
                true
            }

            is AudioException.PermissionDeniedException -> {
                Timber.w(exception, "Permission denied: %s", exception.message)
                true
            }

            is AudioException.AudioProcessingException -> {
                Timber.e(exception, "Audio processing error: %s", exception.message)
                true
            }

            is AudioException.UnsupportedFormatException -> {
                Timber.e(exception, "Unsupported audio format: %s", exception.message)
                true
            }

            is AudioException.NoDeviceConnectedException -> {
                Timber.w(exception, "No device connected: %s", exception.message)
                true
            }

            is AudioException.ResourceUnavailableException -> {
                Timber.w(exception, "Resource unavailable: %s", exception.message)
                true
            }

            else -> {
                // Unrecognized exception type
                Timber.e(exception, "Unexpected error in AudioWave library")
                false
            }
        }

        return Triple(getErrorMessage(exception), isRecognized, exception)
    }

    /**
     * Runs a suspending block and returns a Result object that encapsulates the success or failure.
     *
     * @param block The suspending block to execute
     * @return A Result object containing either the success value or the caught exception
     */
    inline fun <T> runCatching(block: () -> T): Result<T>
    {
        return try { Result.success(block()) }
        catch (e: Throwable)
        {
            handleException(e) // Log the error appropriately
            Result.failure(e)
        }
    }

    /**
     * Creates a flow that safely executes the given function, handling exceptions.
     *
     * @param block The function to execute that produces a value
     * @return A flow that emits the result or handles the exception
     */
    fun <T> flowCatching(block: () -> T): Flow<T> = flow {
        try { emit(block()) }
        catch (e: Throwable)
        {
            handleException(e) // Log the error appropriately
            throw e // Re-throw to be caught by the downstream catch operator
        }
    }

    /**
     * Extension function to safely execute a function on any object.
     *
     * @param block The function to execute
     * @return A result containing either the success value or the failure exception
     */
    inline fun <T, R> T.runCatching(block: T.() -> R): Result<R>
    {
        return try { Result.success(block()) }
        catch (e: Throwable)
        {
            handleException(e) // Log the error appropriately
            Result.failure(e)
        }
    }
}