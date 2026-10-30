package nl.jolanrensen.kodex.gradle.sync

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import nl.jolanrensen.kodex.gradle.KodexSourceSetConfiguration
import nl.jolanrensen.kodex.tooling.KodexGradleModel
import nl.jolanrensen.kodex.tooling.KodexPreprocessModel
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

/**
 * Hooks into the Gradle sync of the IDE to request the [KodexGradleModel] of every Gradle project.
 *
 * The model is built on Gradle's side by the KoDEx Gradle plugin (`KodexToolingModelBuilder`), so it is only
 * present for projects applying that plugin (and a version of it that has the model builder).
 * For those, the configuration is attached to the module's [DataNode] as [KodexGradleModuleData] and later
 * imported by [KodexGradleDataService].
 *
 * Registered in `kodex-gradle.xml` (optional dependency on the Gradle plugin of the IDE).
 */
class KodexGradleProjectResolverExtension : AbstractProjectResolverExtension() {

    private val logger = logger<KodexGradleProjectResolverExtension>()

    /** The models to request per Gradle project. */
    override fun getExtraProjectModelClasses(): Set<Class<*>> = setOf(KodexGradleModel::class.java)

    /**
     * The Tooling API adapts the model to [KodexGradleModel] inside the Gradle daemon, so the jar with
     * this interface (and anything it refers to) must be shipped to the Gradle side.
     * That jar (`kodex-tooling-model`) is plain Java without dependencies for this reason.
     */
    override fun getToolingExtensionsClasses(): Set<Class<*>> =
        setOf(KodexGradleModel::class.java, KodexPreprocessModel::class.java)

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val model = resolverCtx.getExtraProject(gradleModule, KodexGradleModel::class.java)
        if (model != null) {
            try {
                val configurations = model.preprocessConfigurations.map { it.toConfiguration(model.projectPath) }
                ideModule.createChild(
                    KodexGradleModuleData.KEY,
                    KodexGradleModuleData(
                        kodexVersion = model.kodexVersion,
                        configurations = configurations,
                    ),
                )
                logger.info(
                    "KoDEx: found ${configurations.size} preprocess configuration(s) in Gradle project " +
                        "'${model.projectPath}': ${configurations.map { it.displayName }}",
                )
            } catch (e: Exception) {
                logger.warn("KoDEx: could not read the KoDEx configuration of Gradle module '${gradleModule.name}'", e)
            }
        }

        nextResolver.populateModuleExtraModels(gradleModule, ideModule)
    }

    private fun KodexPreprocessModel.toConfiguration(projectPath: String): KodexSourceSetConfiguration =
        KodexSourceSetConfiguration(
            projectPath = projectPath,
            sourceSetName = sourceSetName,
            newSourceSetName = newSourceSetName,
            taskName = taskName,
            sourceRoots = sourceRoots.map { it.absolutePath },
            contextualSourceRoots = contextualSourceRoots.map { it.absolutePath },
            contextualSourceSetNames = contextualSourceSetNames.toList(),
            crossModuleContextualSourceSets = crossModuleContextualSourceSets.toList(),
            processors = processors.toList(),
            arguments = arguments.mapValues { (_, value) -> KodexSourceSetConfiguration.encodeArgument(value) },
            pluginClasspath = pluginClasspath.map { it.absolutePath },
            processLimit = processLimit,
        )
}
