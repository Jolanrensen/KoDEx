package nl.jolanrensen.kodex.gradle

import java.io.File
import java.io.Serializable

/**
 * The KoDEx configuration of a single `kodex { preprocess(sourceSet) { ... } }` call of a Gradle project,
 * as imported from the build during Gradle sync (see `KodexGradleProjectResolverExtension`).
 *
 * This is the IDE-side, [Serializable] copy of `nl.jolanrensen.kodex.tooling.KodexPreprocessModel`.
 * All paths are absolute.
 */
data class KodexSourceSetConfiguration(
    /** The path of the Gradle project, e.g. `":"` or `":lib"`. */
    val projectPath: String,
    /** The name of the Kotlin source set that is preprocessed, e.g. `"main"`. */
    val sourceSetName: String,
    /** The name of the source set KoDEx generates, e.g. `"mainKodex"`. */
    val newSourceSetName: String,
    /** The name of the Gradle task running KoDEx for this source set, e.g. `"preprocessMainKodex"`. */
    val taskName: String,
    /** The source root directories of [sourceSetName]. */
    val sourceRoots: List<String>,
    /** The source roots of all contextual source sets (same project and cross-module), resolved to directories. */
    val contextualSourceRoots: List<String>,
    /** Names of the contextual source sets living in the same Gradle project. */
    val contextualSourceSetNames: List<String>,
    /** Contextual source sets living in other Gradle projects, as `"<projectPath>/<sourceSetName>"`. */
    val crossModuleContextualSourceSets: List<String>,
    /** The fully qualified names of the processors to run, in order. */
    val processors: List<String>,
    /** The arguments for the processors, see [encodeArgument]. */
    val arguments: Map<String, String>,
    /** The jars of KoDEx plugins (custom processors) and their dependencies. */
    val pluginClasspath: List<String>,
    val processLimit: Int,
) : Serializable {

    /** Human-readable identifier, like `:lib/main`. */
    val displayName: String get() = "$projectPath/$sourceSetName"

    /** The decoded [arguments], see [decodeArgument]. */
    val decodedArguments: Map<String, Any?> get() = arguments.mapValues { (_, v) -> decodeArgument(v) }

    /** Everything that can be referenced from [sourceRoots]: the roots themselves and the contextual ones. */
    val visibleSourceRoots: List<String> get() = (sourceRoots + contextualSourceRoots).distinct()

    fun containsInSources(file: File): Boolean = sourceRoots.any { file.isUnder(it) }

    fun containsInContextualSources(file: File): Boolean = contextualSourceRoots.any { file.isUnder(it) }

    /**
     * Identifies the processors and their configuration, so instances/caches can be shared between
     * configurations that run the exact same processors (like `main` and `test` of a project with default settings).
     */
    val processorsKey: ProcessorsKey get() = ProcessorsKey(processors, arguments, pluginClasspath)

    data class ProcessorsKey(
        val processors: List<String>,
        val arguments: Map<String, String>,
        val pluginClasspath: List<String>,
    ) : Serializable

    companion object {
        /**
         * Arguments are stored as strings (for persistence), but the processors need them typed
         * (like [Boolean] for `ARG_DOC_PROCESSOR_LOG_NOT_FOUND`). We prefix the value with its type.
         */
        fun encodeArgument(value: Any?): String =
            when (value) {
                null -> "n:"
                is Boolean -> "b:$value"
                is Int -> "i:$value"
                is Long -> "l:$value"
                is Double, is Float -> "d:$value"
                else -> "s:$value"
            }

        /** Inverse of [encodeArgument]. */
        fun decodeArgument(encoded: String): Any? {
            val value = encoded.substringAfter(':')
            return when (encoded.substringBefore(':', missingDelimiterValue = "s")) {
                "n" -> null
                "b" -> value.toBoolean()
                "i" -> value.toIntOrNull() ?: value
                "l" -> value.toLongOrNull() ?: value
                "d" -> value.toDoubleOrNull() ?: value
                else -> value
            }
        }
    }
}

/**
 * How KoDEx should treat a given file: [Standalone] (the plugin's built-in defaults)
 * or [Connected] to a `preprocess` configuration of the Gradle build.
 */
sealed interface KodexFileContext {

    /**
     * No Gradle configuration applies to the file (no KoDEx in the build, Gradle connection disabled, the file
     * is not part of any `preprocess`ed source set, or no sync has happened yet).
     * The built-in default processors are used and references can be resolved anywhere in the project.
     */
    data object Standalone : KodexFileContext

    /**
     * The file is covered by a `kodex { preprocess(...) }` configuration of the Gradle build.
     *
     * @property configuration the configuration whose processors and arguments are used for the file.
     * @property visibleSourceRoots the only source roots from which references (like `@include [..]`) may be
     *   resolved, mirroring what the Gradle task would see: the preprocessed sources and their contextual sources.
     * @property isContextualOnly `true` when the file is not preprocessed itself but only serves as context for
     *   [configuration] (and possibly other configurations, whose roots are also included in [visibleSourceRoots]).
     */
    data class Connected(
        val configuration: KodexSourceSetConfiguration,
        val visibleSourceRoots: List<String>,
        val isContextualOnly: Boolean = false,
    ) : KodexFileContext {
        fun isVisible(file: File): Boolean = visibleSourceRoots.any { file.isUnder(it) }
    }
}

/**
 * Resolves the [KodexFileContext] of a [file] given all imported [configurations].
 *
 * - If the file is in the source roots of a configuration, that configuration is used
 *   (the one with the most specific source root wins when they overlap).
 * - Else, if the file is in the contextual source roots of one or more configurations, it is only context:
 *   its processors are taken from the first such configuration, and all of their visible roots are combined, as
 *   that is where its own references can be resolved in those Gradle runs.
 * - Else, [KodexFileContext.Standalone].
 */
fun resolveKodexFileContext(file: File, configurations: List<KodexSourceSetConfiguration>): KodexFileContext {
    if (configurations.isEmpty()) return KodexFileContext.Standalone

    val owner = configurations
        .filter { it.containsInSources(file) }
        .maxByOrNull { config -> config.sourceRoots.filter { file.isUnder(it) }.maxOf { it.length } }
    if (owner != null) {
        return KodexFileContext.Connected(
            configuration = owner,
            visibleSourceRoots = owner.visibleSourceRoots,
        )
    }

    val usedAsContextBy = configurations.filter { it.containsInContextualSources(file) }
    if (usedAsContextBy.isNotEmpty()) {
        return KodexFileContext.Connected(
            configuration = usedAsContextBy.first(),
            visibleSourceRoots = usedAsContextBy.flatMap { it.visibleSourceRoots }.distinct(),
            isContextualOnly = true,
        )
    }

    return KodexFileContext.Standalone
}

/** Whether this file is [root] itself or lives inside the directory [root]. */
internal fun File.isUnder(root: String): Boolean {
    val filePath = this.absoluteFile.normalize().path
    val rootPath = File(root).absoluteFile.normalize().path
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}
