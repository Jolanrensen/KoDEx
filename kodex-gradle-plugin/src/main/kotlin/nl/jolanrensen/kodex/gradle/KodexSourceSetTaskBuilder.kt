package nl.jolanrensen.kodex.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import javax.inject.Inject

abstract class KodexSourceSetTaskBuilder
@Inject
constructor(
    sourceSetName: String,
    factory: ObjectFactory,
    project: Project,
) : CommonKodexTaskProperties {

    init {
        applyConventions(project, factory, sourceSetName)
    }

    @get:Input
    internal val inputSourceSet: Property<KotlinSourceSet> = factory.property<KotlinSourceSet>()

    @get:Input
    val isMainSourceSet: Property<Boolean> = factory.property<Boolean>()
        .convention(inputSourceSet.map { it.name == "main" })

    fun isMainSourceSet(boolean: Boolean): Unit = isMainSourceSet.set(boolean)

    @get:Input
    val newSourceSetName: Property<String> = factory.property<String>()
        .convention(inputSourceSet.map { it.name + "Kodex" })

    fun newSourceSetName(string: String): Unit = newSourceSetName.set(string)

    /**
     * Contextual source sets that live the same Gradle project. Unlike [contextualSourceSetsFromOtherProjects].
     */
    @get:Input
    val contextualSourceSets: SetProperty<KotlinSourceSet> = factory.setProperty<KotlinSourceSet>()
        .convention(emptySet())

    /**
     * Contextual source sets that live the same Gradle project. Unlike [contextualSourceSetsFromOtherProjects].
     */
    fun contextualSourceSets(sourceSets: Iterable<KotlinSourceSet>): Unit =
        contextualSourceSets.addAll(sourceSets)

    /**
     * Contextual source sets that live the same Gradle project. Unlike [contextualSourceSetsFromOtherProjects].
     */
    fun contextualSourceSets(first: KotlinSourceSet, vararg others: KotlinSourceSet) {
        contextualSourceSets.add(first)
        contextualSourceSets.addAll(*others)
    }

    /**
     * Contextual source sets that live the same Gradle project. Unlike [contextualSourceSetsFromOtherProjects].
     */
    @JvmName("contextualSourceSetsProvider")
    fun contextualSourceSets(sourceSets: Iterable<Provider<KotlinSourceSet>>): Unit =
        sourceSets.forEach { contextualSourceSets.add(it) }

    /**
     * Contextual source sets that live the same Gradle project. Unlike [contextualSourceSetsFromOtherProjects].
     */
    @JvmName("contextualSourceSetsProvider")
    fun contextualSourceSets(first: Provider<KotlinSourceSet>, vararg others: Provider<KotlinSourceSet>): Unit {
        contextualSourceSets.add(first)
        others.forEach { contextualSourceSets.add(it) }
    }

    /**
     * Contextual source sets that live in another Gradle project. Unlike [contextualSourceSets],
     * these cannot be referenced as [KotlinSourceSet] objects across the project boundary, so they
     * are resolved lazily through variant-aware configurations. The other project's KoDEx output
     * cache and source directories are carried over, letting their documentables resolve without
     * re-analysing them here.
     */
    @get:Internal
    internal val contextualSourceSetsFromOtherProjects: SetProperty<CrossModuleContextualSourceSet> =
        factory.setProperty<CrossModuleContextualSourceSet>().convention(emptySet())

    /**
     * Adds the source set named [sourceSetName] from the project at [projectPath] (e.g. `":other"`)
     * as a contextual source set. The other project must also apply the KoDEx plugin and `preprocess`
     * that source set. Only works for projects within the same build.
     */
    fun contextualSourceSet(projectPath: String, sourceSetName: String = "main"): Unit =
        contextualSourceSetsFromOtherProjects.add(
            CrossModuleContextualSourceSet(projectPath, sourceSetName),
        )

    /**
     * Adds the source set named [sourceSetName] from the [project]
     * as a contextual source set. The other project must also apply the KoDEx plugin and `preprocess`
     * that source set. Only works for projects within the same build.
     */
    fun contextualSourceSet(project: Project, sourceSetName: String = "main"): Unit =
        contextualSourceSet(project.path, sourceSetName)

    @get:Input
    val taskName: Property<String> = factory.property<String>()
        .convention(
            newSourceSetName.map { "preprocess${it.replaceFirstChar { it.titlecase() }}" },
        )

    fun taskName(string: String): Unit = taskName.set(string)

    /**
     * Whether to generate a jar file for the KoDEx-processed source set.
     *
     * Creates the Gradle task "kodex${sourceSetName}Jar" if true.
     *
     * Defaults to true for the main source set.
     *
     * NOTE: This does not yet work for multiplatform projects, set it to `false` for now.
     */
    @get:Input
    val generateJar: Property<Boolean> = factory.property<Boolean>()
        .convention(isMainSourceSet)

    fun generateJar(boolean: Boolean): Unit = generateJar.set(boolean)

    /**
     * Whether to generate a sources jar file for the KoDEx-processed source set.
     *
     * Creates the Gradle task "kodex${sourceSetName}SourcesJar" if true.
     *
     * Defaults to true for the main source set.
     *
     * NOTE: This does not yet work for multiplatform projects, set it to `false` for now.
     */
    @get:Input
    val generateSourcesJar: Property<Boolean> = factory.property<Boolean>()
        .convention(isMainSourceSet)

    fun generateSourcesJar(boolean: Boolean): Unit = generateSourcesJar.set(boolean)

    @get:Internal
    internal val runOnTask: ListProperty<Action<RunKodexTask>> = factory.listProperty<Action<RunKodexTask>>()
        .convention(listOf())

    fun task(action: Action<RunKodexTask>) {
        runOnTask.add(action)
    }
}
