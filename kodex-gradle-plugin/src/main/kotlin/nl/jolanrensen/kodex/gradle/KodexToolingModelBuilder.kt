package nl.jolanrensen.kodex.gradle

import nl.jolanrensen.kodex.tooling.KodexGradleModel
import nl.jolanrensen.kodex.tooling.KodexPreprocessModel
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File
import java.io.Serializable

/**
 * Builds the [KodexGradleModel] for a project, so the KoDEx IntelliJ plugin can read the KoDEx configuration
 * of the build (processors, arguments, contextual source sets, plugins) during Gradle sync and run
 * "connected" to the build instead of "standalone".
 *
 * Registered by [KodexPlugin]. The model is requested by the IDE with the name of [KodexGradleModel].
 * Only [Serializable] JDK types are used in the model implementation so it can be transferred
 * to the IDE through the Tooling API.
 */
internal class KodexToolingModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean = modelName == KodexGradleModel::class.java.name

    override fun buildAll(modelName: String, project: Project): Any {
        val extension = project.extensions.findByType(KodexExtension::class.java)

        return DefaultKodexGradleModel(
            kodexVersion = KodexPlugin::class.java.`package`?.implementationVersion ?: "unknown",
            projectPath = project.path,
            projectDir = project.projectDir,
            preprocessConfigurations = extension
                ?.taskCreators
                ?.map { project.buildPreprocessModel(it) }
                .orEmpty(),
        )
    }

    private fun Project.buildPreprocessModel(taskCreator: KodexSourceSetTaskBuilder): DefaultKodexPreprocessModel {
        // the task is realized and fully configured, since models are built after project evaluation
        val task = taskCreator.task.get()

        val contextualSourceRoots = try {
            task.contextualSources.get().flatten()
        } catch (e: Exception) {
            logger.warn(
                "KoDEx: Could not resolve the contextual sources of task '${task.name}' for the IDE: ${e.message}",
            )
            taskCreator.contextualSourceSets.get().flatMap { it.kotlin.sourceDirectories.files }
        }

        return DefaultKodexPreprocessModel(
            sourceSetName = taskCreator.inputSourceSet.get().name,
            newSourceSetName = taskCreator.newSourceSetName.get(),
            taskName = task.name,
            sourceRoots = task.sources.get().map { it.absoluteFile },
            contextualSourceRoots = contextualSourceRoots.map { it.absoluteFile }.distinct(),
            contextualSourceSetNames = taskCreator.contextualSourceSets.get().map { it.name },
            crossModuleContextualSourceSets = taskCreator.contextualSourceSetsFromOtherProjects.get()
                .map { "${it.projectPath}/${it.sourceSetName}" },
            processors = task.processors.get(),
            arguments = task.arguments.get().mapValues { (_, value) -> value.toSerializableArgument() },
            pluginClasspath = resolvePluginClasspath(task),
            processLimit = task.processLimit.get(),
        )
    }

    /**
     * Resolves only the KoDEx plugins (declared with `dependencies { plugin(...) }`) of the [task]'s classpath,
     * without the Dokka dependencies KoDEx adds for itself in [maybeCreateRuntimeConfiguration].
     * These are the jars the IDE needs to be able to load custom processors.
     *
     * Resolution is lenient, so an unresolvable plugin doesn't break the Gradle sync.
     */
    private fun Project.resolvePluginClasspath(task: RunKodexTask): List<File> {
        val classpath = task.classpath.orNull ?: return emptyList()

        return try {
            when (classpath) {
                is Configuration -> {
                    val pluginDependencies = classpath.allDependencies.filter { !it.isKodexRuntimeDependency() }
                    if (pluginDependencies.isEmpty()) return emptyList()

                    configurations
                        .detachedConfiguration(*pluginDependencies.toTypedArray())
                        .apply { isCanBeConsumed = false }
                        .incoming
                        .artifactView { it.lenient(true) }
                        .files
                        .files
                        .toList()
                }

                // a user-supplied file collection, use it as is
                else -> classpath.files.toList()
            }
        } catch (e: Exception) {
            logger.warn(
                "KoDEx: Could not resolve the plugin classpath of task '${task.name}' for the IDE: ${e.message}",
            )
            emptyList()
        }
    }
}

/**
 * The Dokka (analysis) dependencies KoDEx itself needs to run on Gradle's side,
 * see [maybeCreateRuntimeConfiguration].
 */
private fun Dependency.isKodexRuntimeDependency(): Boolean = this is ModuleDependency && group == "org.jetbrains.dokka"

/** Only [String], [Boolean], and [Number] arguments can be transferred to the IDE, others are stringified. */
private fun Any?.toSerializableArgument(): Any? =
    when (this) {
        null, is String, is Boolean, is Number -> this
        else -> toString()
    }

internal data class DefaultKodexGradleModel(
    private val kodexVersion: String,
    private val projectPath: String,
    private val projectDir: File,
    private val preprocessConfigurations: List<KodexPreprocessModel>,
) : KodexGradleModel,
    Serializable {
    override fun getKodexVersion(): String = kodexVersion

    override fun getProjectPath(): String = projectPath

    override fun getProjectDir(): File = projectDir

    override fun getPreprocessConfigurations(): List<KodexPreprocessModel> = preprocessConfigurations
}

internal data class DefaultKodexPreprocessModel(
    private val sourceSetName: String,
    private val newSourceSetName: String,
    private val taskName: String,
    private val sourceRoots: List<File>,
    private val contextualSourceRoots: List<File>,
    private val contextualSourceSetNames: List<String>,
    private val crossModuleContextualSourceSets: List<String>,
    private val processors: List<String>,
    private val arguments: Map<String, Any?>,
    private val pluginClasspath: List<File>,
    private val processLimit: Int,
) : KodexPreprocessModel,
    Serializable {
    override fun getSourceSetName(): String = sourceSetName

    override fun getNewSourceSetName(): String = newSourceSetName

    override fun getTaskName(): String = taskName

    override fun getSourceRoots(): List<File> = sourceRoots

    override fun getContextualSourceRoots(): List<File> = contextualSourceRoots

    override fun getContextualSourceSetNames(): List<String> = contextualSourceSetNames

    override fun getCrossModuleContextualSourceSets(): List<String> = crossModuleContextualSourceSets

    override fun getProcessors(): List<String> = processors

    override fun getArguments(): Map<String, Any?> = arguments

    override fun getPluginClasspath(): List<File> = pluginClasspath

    override fun getProcessLimit(): Int = processLimit
}
