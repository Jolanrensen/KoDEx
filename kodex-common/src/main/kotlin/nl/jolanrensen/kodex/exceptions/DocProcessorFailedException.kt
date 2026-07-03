package nl.jolanrensen.kodex.exceptions

/**
 * Exception that is thrown when a [DocProcessor] fails.
 */
open class DocProcessorFailedException(
    val processorName: String,
    cause: Throwable? = null,
    message: String = "Doc processor $processorName failed: ${cause?.message}",
) : RuntimeException(message, cause)
