package nl.jolanrensen.kodex.gradle

import io.github.oshai.kotlinlogging.DelegatingKLogger
import io.github.oshai.kotlinlogging.KLogger
import org.gradle.api.logging.Logger

fun KLogger.lifecycle(msg: () -> Any?) {
    this as DelegatingKLogger<*>
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    val underlyingLogger = underlyingLogger as Logger

    if (underlyingLogger.isLifecycleEnabled) {
        underlyingLogger.lifecycle(msg().toString()) // unsafe!
    }
}

fun KLogger.lifecycle(throwable: Throwable, msg: () -> Any?) {
    this as DelegatingKLogger<*>
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    val underlyingLogger = underlyingLogger as Logger

    if (underlyingLogger.isLifecycleEnabled) {
        underlyingLogger.lifecycle(msg().toString(), throwable) // unsafe!
    }
}

/**
 * Logs the given message at lifecycle log level.
 *
 * @param message the log message.
 */
fun KLogger.lifecycle(message: String?) {
    this as DelegatingKLogger<*>
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    (underlyingLogger as Logger).lifecycle(message)
}

/**
 * Logs the given message at lifecycle log level.
 *
 * @param message the log message.
 * @param objects the log message parameters.
 */
fun KLogger.lifecycle(message: String?, vararg objects: Any?) {
    this as DelegatingKLogger<*>
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    (underlyingLogger as Logger).lifecycle(message, *objects)
}

/**
 * Logs the given message at lifecycle log level.
 *
 * @param message the log message.
 * @param throwable the exception to log.
 */
fun KLogger.lifecycle(message: String?, throwable: Throwable?) {
    this as DelegatingKLogger<*>
    require(underlyingLogger is Logger) { "Logger must be org.gradle.api.logging.Logger for this." }
    (underlyingLogger as Logger).lifecycle(message, throwable)
}
