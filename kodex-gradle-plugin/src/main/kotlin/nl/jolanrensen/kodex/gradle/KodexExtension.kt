package nl.jolanrensen.kodex.gradle

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import javax.inject.Inject

/**
 * ## EXPERIMENTAL
 *
 * The main extension for KoDEx.
 *
 * Here you can attach KoDEx to your Kotlin source sets.
 *
 * ```kt
 * kodex {
 *     preprocess(kotlin.sourceSets["mySourceSet"]) {
 *         generateJar = true
 *         generateSourcesJar = true
 *
 *         processors = listOf(
 *              ...
 *         )
 *         ...
 *     }
 * }
 * ```
 */
abstract class KodexExtension
    @Inject
    constructor(
        private val project: Project,
        private val factory: ObjectFactory,
    ) {
        internal val taskCreators = mutableSetOf<KodexSourceSetTaskBuilder>()

        /**
         * Attaches KoDEx to the given [sourceSet].
         *  - Creates a new SourceSet named "${sourceSetName}Kodex" by default ([KodexSourceSetTaskBuilder.newSourceSetName]).
         *  - Creates a new task for the given source set named "preprocess${newSourceSetName}" by default ([KodexSourceSetTaskBuilder.taskName]).
         *  - This task will preprocess the given source set and store the results at
         *  `build/kodex/$newSourceSetName` by default ([KodexSourceSetTaskBuilder.target]).
         *  - Creates a jar task if [sourceSet] is the main source set, and we're not multiplatform
         *  ([KodexSourceSetTaskBuilder.generateJar]).
         *  - Creates a sources jar task if [sourceSet] is the main source set, and we're not multiplatform
         *  ([KodexSourceSetTaskBuilder.generateSourcesJar]).
         *
         * @see KodexSourceSetTaskBuilder
         */
        public fun preprocess(
            sourceSet: NamedDomainObjectProvider<KotlinSourceSet>,
            block: KodexSourceSetTaskBuilder.() -> Unit = {},
        ) = preprocess(sourceSet.get(), block)

        /**
         * Attaches KoDEx to the given [sourceSet].
         *  - Creates a new SourceSet named "${sourceSetName}Kodex" by default ([KodexSourceSetTaskBuilder.newSourceSetName]).
         *  - Creates a new task for the given source set named "preprocess${newSourceSetName}" by default ([KodexSourceSetTaskBuilder.taskName]).
         *  - This task will preprocess the given source set and store the results at
         *  `build/kodex/$newSourceSetName` by default ([KodexSourceSetTaskBuilder.target]).
         *  - Creates a jar task if [sourceSet] is the main source set, and we're not multiplatform
         *  ([KodexSourceSetTaskBuilder.generateJar]).
         *  - Creates a sources jar task if [sourceSet] is the main source set, and we're not multiplatform
         *  ([KodexSourceSetTaskBuilder.generateSourcesJar]).
         *
         * @see KodexSourceSetTaskBuilder
         */
        public fun preprocess(sourceSet: KotlinSourceSet, block: KodexSourceSetTaskBuilder.() -> Unit = {}) {
            val creator = factory.newInstance(
                KodexSourceSetTaskBuilder::class.java,
                sourceSet.name + "Kodex",
            )
            creator.inputSourceSet.set(sourceSet)
            creator.block()

            // registered right away, so the task can be referenced (and configured) from build scripts.
            // The defaults from `creator` are applied (lazily) in the registration action, so they are applied
            // before any user `tasks.named(...) { }` actions, which can thus override them.
            // The rest of the wiring is done by KodexPlugin after the project is evaluated.
            creator.task = project.tasks.register(creator.taskName.get(), RunKodexTask::class.java) {
                it.configureFrom(creator)
            }
            taskCreators.add(creator)
        }

        private fun RunKodexTask.configureFrom(creator: KodexSourceSetTaskBuilder) {
            val inputSourceSet = creator.inputSourceSet
            group = "KoDEx"
            description =
                "Runs KoDEx ${creator.newSourceSetName.get()} on the ${inputSourceSet.get().name} sources"
            sources.set(
                project.provider { inputSourceSet.get().kotlin.sourceDirectories.toList() },
            )
            contextualSources.set(
                creator.contextualSourceSets.map { sourceSets ->
                    sourceSets.map { it.kotlin.sourceDirectories.toList() }
                },
            )
            applyPropertiesFrom(creator)
            creator.runOnTask.get().forEach {
                it.execute(this)
            }
        }
    }
