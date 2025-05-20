package org.operatorfoundation.audiowave.utils

import timber.log.Timber

/**
 * Utility class for validating audio processing parameters with clear error messages.
 *
 * This class provides methods to validate and constrain parameter values
 * commonly used in audio processing, such as ensuring values are within
 * specific ranges or meet certain criteria.
 *
 * Example usage:
 * ```
 * // Validate gain parameter
 * val validGain = ParameterValidator.validateRange(inputGain, 0f, 5f, "Gain")
 * ```
 */
object ParameterValidator
{

    /**
     * Validate that a value falls within a specified range, constraining it if necessary.
     *
     * @param value The value to validate
     * @param min The minimum allowed value
     * @param max The maximum allowed value
     * @param paramName The name of the parameter (for logging)
     * @return The validated value, constrained to the specified range
     */
    fun validateRange(value: Float, min: Float, max: Float, paramName: String): Float
    {
        val limitedValue = AudioUtils.limit(value, min, max)
        if (limitedValue != value) {
            Timber.w("$paramName value $value outside valid range [$min, $max], using $limitedValue instead")
        }
        return limitedValue
    }

    /**
     * Validate that a value is non-negative, constraining it to zero if necessary.
     *
     * @param value The value to validate
     * @param paramName The name of the parameter (for logging)
     * @return The validated value, set to zero if it was negative
     */
    fun validateNonNegative(value: Float, paramName: String): Float
    {
        return validateRange(value, 0f, Float.MAX_VALUE, paramName)
    }

    /**
     * Validate that a value is positive (greater than zero), constraining it if necessary.
     *
     * @param value The value to validate
     * @param paramName The name of the parameter (for logging)
     * @return The validated value, set to a small positive value if it was zero or negative
     */
    fun validatePositive(value: Float, paramName: String): Float
    {
        val minValue = 0.00001f  // Small positive value
        return validateRange(value, minValue, Float.MAX_VALUE, paramName)
    }

    /**
     * Validate that a value is between 0.0 and 1.0, constraining it if necessary.
     * Useful for parameters representing percentages or normalized values.
     *
     * @param value The value to validate
     * @param paramName The name of the parameter (for logging)
     * @return The validated value, constrained to the range [0.0, 1.0]
     */
    fun validateNormalized(value: Float, paramName: String): Float
    {
        return validateRange(value, 0f, 1f, paramName)
    }

    /**
     * Validate that an array length matches an expected value.
     *
     * @param array The array to validate
     * @param expectedLength The expected length of the array
     * @param paramName The name of the parameter (for logging)
     * @return Result with value true if valid, or failure with error message if invalid
     */
    fun validateArrayLength(array: Array<*>, expectedLength: Int, paramName: String): Result<Boolean>
    {
        return ErrorHandler.runCatching {
            val isValid = array.size == expectedLength
            if (!isValid) {
                Timber.w("$paramName has invalid length ${array.size}, expected $expectedLength")
                throw IllegalArgumentException("$paramName has invalid length ${array.size}, expected $expectedLength")
            }

            isValid
        }
    }

    /**
     * Validate that a parameter is one of the allowed values.
     *
     * @param value The value to validate
     * @param allowedValues The set of allowed values
     * @param paramName The name of the parameter (for logging)
     * @param defaultValue The default value to use if the provided value is not allowed
     * @return The validated value (original if valid, default if not)
     */
    fun <T> validateAllowedValue(value: T, allowedValues: Set<T>, paramName: String, defaultValue: T): T
    {
        if (value !in allowedValues)
        {
            Timber.w("$paramName value $value is not allowed. Using default value $defaultValue")
            return defaultValue
        }

        return value
    }
}
