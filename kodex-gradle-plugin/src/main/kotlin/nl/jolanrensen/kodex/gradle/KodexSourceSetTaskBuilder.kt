package nl.jolanrensen.kodex.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
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

    @get:Input
    val contextualSourceSets: SetProperty<KotlinSourceSet> = factory.setProperty<KotlinSourceSet>()
        .convention(emptySet())

    fun contextualSourceSets(sourceSets: Iterable<KotlinSourceSet>): Unit =
        contextualSourceSets.addAll(sourceSets)

    fun contextualSourceSets(first: KotlinSourceSet, vararg others: KotlinSourceSet): Unit {
        contextualSourceSets.add(first)
        contextualSourceSets.addAll(*others)
    }

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
