package nl.jolanrensen.kodex.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.creating
import java.io.File

/**
 * Create a new [RunKodexTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by tasks.creatingRunKodexTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun TaskContainer.creatingRunKodexTask(sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    creating(RunKodexTask::class) {
        this.sources.set(sources)
        block()
    }

/**
 * Create a new [RunKodexTask].
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * tasks.creatingRunKodexTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun TaskContainer.createRunKodexTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    create<RunKodexTask>(name) {
        this.sources.set(sources)
        block()
    }

/**
 * Create a new [RunKodexTask] if one with this name doesn't already exist.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * tasks.maybeCreateRunKodexTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun TaskContainer.maybeCreateRunKodexTask(
    name: String,
    sources: Iterable<File>,
    block: RunKodexTask.() -> Unit,
) = maybeCreate(name, RunKodexTask::class.java).apply {
    this.sources.set(sources)
    block()
}

/**
 * Create a new [RunKodexTask] if one with this name doesn't already exist.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * maybeCreateRunKodexTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 */
public fun Project.maybeCreateRunKodexTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.maybeCreateRunKodexTask(name, sources, block)

/**
 * Create a new [RunKodexTask].
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * creatingRunKodexTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun Project.createRunKodexTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.createRunKodexTask(name, sources, block)

/**
 * Create a new [RunKodexTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by creatingRunKodexTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun Project.creatingRunKodexTask(sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.creatingRunKodexTask(sources, block)

/**
 * Create a new [RunKodexTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by tasks.creatingProcessDocTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Deprecated("Use creatingRunKodexTask instead", ReplaceWith("this.creatingRunKodexTask(sources, block)"))
public fun TaskContainer.creatingProcessDocTask(sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    creating(RunKodexTask::class) {
        this.sources.set(sources)
        block()
    }

/**
 * Create a new [RunKodexTask].
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * tasks.creatingProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Deprecated("Use createRunKodexTask instead", ReplaceWith("this.createRunKodexTask(name, sources, block)"))
public fun TaskContainer.createProcessDocTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    create<RunKodexTask>(name) {
        this.sources.set(sources)
        block()
    }

/**
 * Create a new [RunKodexTask] if one with this name doesn't already exist.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * tasks.maybeCreateProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Deprecated(
    "Use maybeCreateRunKodexTask instead",
    ReplaceWith("this.maybeCreateRunKodexTask(name, sources, block)"),
)
public fun TaskContainer.maybeCreateProcessDocTask(
    name: String,
    sources: Iterable<File>,
    block: RunKodexTask.() -> Unit,
) = maybeCreate(name, RunKodexTask::class.java).apply {
    this.sources.set(sources)
    block()
}

/**
 * Create a new [RunKodexTask] if one with this name doesn't already exist.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * maybeCreateProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 */
@Suppress("DEPRECATION")
@Deprecated(
    "Use maybeCreateRunKodexTask instead",
    ReplaceWith("this.maybeCreateRunKodexTask(name, sources, block)"),
)
public fun Project.maybeCreateProcessDocTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.maybeCreateProcessDocTask(name, sources, block)

/**
 * Create a new [RunKodexTask].
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * creatingProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Suppress("DEPRECATION")
@Deprecated("Use createRunKodexTask instead", ReplaceWith("this.createRunKodexTask(sources, block)"))
public fun Project.createProcessDocTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.createProcessDocTask(name, sources, block)

/**
 * Create a new [RunKodexTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by creatingProcessDocTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Suppress("DEPRECATION")
@Deprecated("Use creatingRunKodexTask instead", ReplaceWith("this.creatingRunKodexTask(sources, block)"))
public fun Project.creatingProcessDocTask(sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.creatingProcessDocTask(sources, block)
