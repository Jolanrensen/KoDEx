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
import org.gradle.api.tasks.TaskProvider
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
        fun contextualSourceSets(sourceSets: Iterable<KotlinSourceSet>): Unit = contextualSourceSets.addAll(sourceSets)

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
        fun contextualSourceSets(first: Provider<KotlinSourceSet>, vararg others: Provider<KotlinSourceSet>) {
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

        /**
         * Lazily adds the source sets named [sourceSetName] from the projects at [projectPaths] (e.g. `":other"`)
         * as contextual source sets.
         * The [projectPaths] provider is queried after the current project has been evaluated, so it can depend on
         * information that is only available at the end of the build script, like the declared dependencies.
         *
         * The other projects must also apply the KoDEx plugin and `preprocess` that source set.
         * Only works for projects within the same build.
         */
        fun contextualSourceSets(projectPaths: Provider<out Iterable<String>>, sourceSetName: String = "main"): Unit =
            contextualSourceSetsFromOtherProjects.addAll(
                projectPaths.map { paths -> paths.map { CrossModuleContextualSourceSet(it, sourceSetName) } },
            )

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

        /**
         * Whether the jar files generated by [generateJar] and [generateSourcesJar] should be built when running
         * the `assemble` task.
         *
         * Defaults to true.
         */
        @get:Input
        val addJarsToAssemble: Property<Boolean> = factory.property<Boolean>()
            .convention(true)

        fun addJarsToAssemble(boolean: Boolean): Unit = addJarsToAssemble.set(boolean)

        /**
         * Whether to replace the artifacts of the `java` software component (which is used by `maven-publish`
         * when publishing `from(components["java"])`) with the KoDEx-processed jar files.
         *
         * If true, the published `jar` artifact will contain the classes compiled from the KoDEx-processed sources
         * (so [@ExcludeFromSources][nl.jolanrensen.kodex.annotations.ExcludeFromSources] is respected)
         * and the published `sources` artifact will contain the KoDEx-processed sources
         * (when [generateJar] and [generateSourcesJar] are enabled, respectively).
         * The published dependencies, attributes, and other variants (like `javadoc`) stay the same.
         *
         * This only replaces the artifacts used for publishing; other projects in the same build that depend on this
         * project still use the normal, unprocessed, jar.
         *
         * Only works for the main source set of a single-target JVM project.
         *
         * Defaults to false.
         */
        @get:Input
        val replaceArtifactsInJavaComponent: Property<Boolean> = factory.property<Boolean>()
            .convention(false)

        fun replaceArtifactsInJavaComponent(boolean: Boolean): Unit = replaceArtifactsInJavaComponent.set(boolean)

        /**
         * The [RunKodexTask] registered for this source set. Registered as soon as [KodexExtension.preprocess] is called,
         * but configured after the project is evaluated.
         */
        @get:Internal
        internal lateinit var task: TaskProvider<RunKodexTask>

        @get:Internal
        internal val runOnTask: ListProperty<Action<RunKodexTask>> = factory.listProperty<Action<RunKodexTask>>()
            .convention(listOf())

        fun task(action: Action<RunKodexTask>) {
            runOnTask.add(action)
        }
    }
