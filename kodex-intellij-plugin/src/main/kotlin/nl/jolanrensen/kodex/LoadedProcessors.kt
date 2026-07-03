package nl.jolanrensen.kodex

import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR_LOG_NOT_FOUND
import nl.jolanrensen.kodex.defaultProcessors.COMMENT_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.EXPORT_AS_HTML_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR_PRE_SORT
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_FILE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.SAMPLE_DOC_PROCESSOR
import nl.jolanrensen.kodex.processor.DocProcessor
import nl.jolanrensen.kodex.processor.findProcessors

fun ClassLoader.getLoadedProcessors(): List<DocProcessor> {
    Thread.currentThread().contextClassLoader = this

    return findProcessors(
        fullyQualifiedNames = listOf(
            COMMENT_DOC_PROCESSOR,
            INCLUDE_DOC_PROCESSOR,
            INCLUDE_FILE_DOC_PROCESSOR,
            ARG_DOC_PROCESSOR,
            SAMPLE_DOC_PROCESSOR,
            EXPORT_AS_HTML_DOC_PROCESSOR,
            REMOVE_ESCAPE_CHARS_PROCESSOR,
        ),
        arguments = mapOf(
            ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false,
            INCLUDE_DOC_PROCESSOR_PRE_SORT to false,
        ),
    )
}

/**
 * Loads all processors that are included in the plugin with the correct settings.
 *
 * TODO make customizable
 */
fun Any.getLoadedProcessors(): List<DocProcessor> = this.javaClass.classLoader.getLoadedProcessors()
