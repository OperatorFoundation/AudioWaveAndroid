package org.operatorfoundation.audiowave.exception

/**
 * Base exception class for audio-related errors in the AudioWave library.
 *
 * This class serves as the parent for all specific audio exception types,
 * providing a consistent exception hierarchy for error handling.
 *
 * @param message A descriptive error message
 * @param cause The underlying cause of this exception, if any
 */
sealed class AudioException(message: String, cause: Throwable? = null) : Exception(message, cause)
{
    /**
     * Exception thrown when there's an issue connecting to or communicating with a USB audio device.
     *
     * @param message A descriptive error message
     * @param cause The underlying cause of this exception, if any
     */
    class DeviceConnectionException(message: String, cause: Throwable? = null) :
        AudioException(message, cause)

    /**
     * Exception thrown when there's an error during audio processing operations.
     *
     * @param message A descriptive error message
     * @param cause The underlying cause of this exception, if any
     */
    class AudioProcessingException(message: String, cause: Throwable? = null) :
        AudioException(message, cause)

    /**
     * Exception thrown when an unsupported audio format is encountered.
     *
     * @param message A descriptive error message
     * @param cause The underlying cause of this exception, if any
     */
    class UnsupportedFormatException(message: String, cause: Throwable? = null) :
        AudioException(message, cause)

    /**
     * Exception thrown when there's a permission issue with a USB device.
     *
     * @param message A descriptive error message
     * @param cause The underlying cause of this exception, if any
     */
    class PermissionDeniedException(message: String, cause: Throwable? = null) :
        AudioException(message, cause)

    /**
     * Exception thrown when an operation is attempted with no device connected.
     *
     * @param message A descriptive error message
     */
    class NoDeviceConnectedException(message: String = "No audio device connected") :
        AudioException(message)

    /**
     * Exception thrown when a specific resource or feature is not available.
     *
     * @param message A descriptive error message
     */
    class ResourceUnavailableException(message: String) :
        AudioException(message)

    /**
     * Thrown when there is an issue configuring a device.
     */
    class DeviceConfigurationException(message: String, cause: Throwable? = null) :
        AudioException(message, cause)

    /**
     * Thrown when an invalid operation is attempted.
     */
    class InvalidOperationException(message: String, cause: Throwable? = null) :
        AudioException(message, cause)

    /**
     * Thrown when a required feature is not supported.
     */
    class UnsupportedFeatureException(message: String, cause: Throwable? = null) :
        AudioException(message, cause)

}