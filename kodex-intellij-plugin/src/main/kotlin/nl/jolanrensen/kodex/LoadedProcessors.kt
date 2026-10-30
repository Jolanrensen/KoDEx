package nl.jolanrensen.kodex

import com.intellij.openapi.diagnostic.logger
import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR_LOG_NOT_FOUND
import nl.jolanrensen.kodex.defaultProcessors.COMMENT_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.EXPORT_AS_HTML_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR_PRE_SORT
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_FILE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REFERENCE_CODE_SPAN_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.SAMPLE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.SAMPLE_DOC_PROCESSOR_THROW_WHEN_NOT_FOUND
import nl.jolanrensen.kodex.gradle.KodexFileContext
import nl.jolanrensen.kodex.gradle.KodexSourceSetConfiguration
import nl.jolanrensen.kodex.processor.DocProcessor
import nl.jolanrensen.kodex.processor.findProcessors
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.ConcurrentHashMap

private val logger = logger<ProcessorLoader>()

/** The processors the plugin runs in "standalone" mode, in the recommended order. */
val standaloneProcessors: List<String> = listOf(
    COMMENT_DOC_PROCESSOR,
    INCLUDE_DOC_PROCESSOR,
    INCLUDE_FILE_DOC_PROCESSOR,
    ARG_DOC_PROCESSOR,
    SAMPLE_DOC_PROCESSOR,
    EXPORT_AS_HTML_DOC_PROCESSOR,
    REFERENCE_CODE_SPAN_DOC_PROCESSOR,
    REMOVE_ESCAPE_CHARS_PROCESSOR,
)

/**
 * Arguments the IDE needs regardless of what the Gradle build says: the IDE renders a single doc at a time
 * (so pre-sorting all docs makes no sense) and should keep rendering instead of failing when
 * something is not found.
 */
private val ideArgumentOverrides: Map<String, Any?> = mapOf(
    INCLUDE_DOC_PROCESSOR_PRE_SORT to false,
    SAMPLE_DOC_PROCESSOR_THROW_WHEN_NOT_FOUND to false,
)

/** Arguments used in standalone mode or when the Gradle build does not specify them. */
private val ideDefaultArguments: Map<String, Any?> = mapOf(
    ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false,
)

/**
 * Loads the processors that are included in the plugin with the "standalone" settings.
 */
fun ClassLoader.getLoadedProcessors(): List<DocProcessor> =
    withContextClassLoader(this) {
        findProcessors(
            fullyQualifiedNames = standaloneProcessors,
            arguments = ideDefaultArguments + ideArgumentOverrides,
        )
    }

/**
 * Loads all processors that are included in the plugin with the "standalone" settings.
 *
 * @see ProcessorLoader for the processors of a specific file, which can come from the Gradle build.
 */
fun Any.getLoadedProcessors(): List<DocProcessor> = this.javaClass.classLoader.getLoadedProcessors()

/**
 * Creates [DocProcessor] instances for a [KodexFileContext]:
 * - [KodexFileContext.Standalone]: the built-in processors with their default arguments, see [getLoadedProcessors].
 * - [KodexFileContext.Connected]: the processors, arguments, and KoDEx plugins (jars with custom processors)
 *   configured in the Gradle build for the source set of the file.
 *
 * Processors are instantiated anew on every [load] call, since a [DocProcessor] instance can only run once.
 * The class loaders for the plugin jars of each configuration are cached, as these are expensive to create
 * and would otherwise produce different `Class` objects for the same processor.
 *
 * Note: the [DocProcessor] classes of a plugin jar are loaded with the KoDEx IntelliJ plugin's class loader as
 * parent, so they see the exact same `kodex-common` the IDE plugin uses. The plugin jar must be compatible
 * with it.
 */
class ProcessorLoader(private val pluginClassLoader: ClassLoader) {

    private val classLoaders = ConcurrentHashMap<List<String>, ClassLoader>()

    /** Fresh instances of the processors for [context]. */
    fun load(context: KodexFileContext): List<DocProcessor> =
        when (context) {
            is KodexFileContext.Standalone -> pluginClassLoader.getLoadedProcessors()
            is KodexFileContext.Connected -> load(context.configuration)
        }

    /** Fresh instances of the processors for [configuration]. */
    fun load(configuration: KodexSourceSetConfiguration): List<DocProcessor> {
        val classLoader = classLoaderFor(configuration.pluginClasspath)
        val arguments = ideDefaultArguments + configuration.decodedArguments + ideArgumentOverrides

        val processors = withContextClassLoader(classLoader) {
            findProcessors(
                fullyQualifiedNames = configuration.processors,
                arguments = arguments,
            )
        }

        val missing = configuration.processors - processors.map { it::class.qualifiedName }.toSet()
        if (missing.isNotEmpty()) {
            logger.warn(
                "KoDEx: processors $missing of ${configuration.displayName} could not be loaded in the IDE, " +
                    "they are skipped. Make sure their plugin is declared with `dependencies { plugin(...) }` " +
                    "and is compatible with the KoDEx IntelliJ plugin.",
            )
        }
        return processors
    }

    /** The fully qualified names of processors in [configuration] that cannot be loaded. */
    fun missingProcessors(configuration: KodexSourceSetConfiguration): List<String> {
        val loaded = load(configuration).map { it::class.qualifiedName }.toSet()
        return configuration.processors.filter { it !in loaded }
    }

    private fun classLoaderFor(pluginClasspath: List<String>): ClassLoader {
        if (pluginClasspath.isEmpty()) return pluginClassLoader

        return classLoaders.getOrPut(pluginClasspath) {
            val urls = pluginClasspath
                .map(::File)
                .filter { it.exists() }
                .map { it.toURI().toURL() }
                .toTypedArray()
            URLClassLoader(urls, pluginClassLoader)
        }
    }

    /** Forgets the cached class loaders, e.g. after a Gradle sync changed the plugin jars. */
    fun reset() {
        classLoaders.clear()
    }
}

/**
 * [findProcessors] uses the [java.util.ServiceLoader] with the context class loader of the current thread,
 * which by default cannot see the plugin's classes.
 */
private inline fun <T> withContextClassLoader(classLoader: ClassLoader, block: () -> T): T {
    val thread = Thread.currentThread()
    val previous = thread.contextClassLoader
    thread.contextClassLoader = classLoader
    try {
        return block()
    } finally {
        thread.contextClassLoader = previous
    }
}
