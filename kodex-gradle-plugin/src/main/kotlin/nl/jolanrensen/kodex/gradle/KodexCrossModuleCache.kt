package nl.jolanrensen.kodex.gradle

import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import java.io.File
import java.io.Serializable

/**
 * Reference to a source set living in another Gradle project, to be used as a contextual source set
 * of a [RunKodexTask]. Resolved lazily through variant-aware configurations, see
 * [Project.exposeContextualCaches] (producer) and [Project.wireCrossModuleContextualCaches] (consumer).
 */
internal data class CrossModuleContextualSourceSet(
    val projectPath: String,
    val sourceSetName: String,
) : Serializable

/** The (original) source set a KoDEx variant belongs to, e.g. `"main"`. */
internal val KODEX_SOURCE_SET_ATTRIBUTE: Attribute<String> =
    Attribute.of("nl.jolanrensen.kodex.sourceSetName", String::class.java)

/**
 * Marks which kind of KoDEx variant a configuration produces/consumes.
 * [KODEX_CACHE_USAGE] or [KODEX_CONTEXTUAL_SOURCES_USAGE].
 */
internal val KODEX_USAGE_ATTRIBUTE: Attribute<String> =
    Attribute.of("nl.jolanrensen.kodex.usage", String::class.java)

/** [KODEX_USAGE_ATTRIBUTE] value for the serialized `DocumentablesByPathMap` output cache (`.bin`). */
internal const val KODEX_CACHE_USAGE = "kodex-output-cache"

/** [KODEX_USAGE_ATTRIBUTE] value for the source directories of a contextual source set. */
internal const val KODEX_CONTEXTUAL_SOURCES_USAGE = "kodex-contextual-sources"

/**
 * Producer side.
 *
 * Exposes, as consumable variants keyed by the [taskCreator]'s source set name:
 *  - [RunKodexTask.outputCacheFile]: the output cache, built by [task].
 *  - the source directories of the input source set.
 *
 * These can be picked up by a KoDEx task in another module that references this source set as a
 * contextual source set (see [wireCrossModuleContextualCaches]). We always expose them, since a
 * producer cannot know whether some other module will consume it.
 */
internal fun Project.exposeContextualCaches(taskCreator: KodexSourceSetTaskBuilder, task: RunKodexTask) {
    val inputSourceSet = taskCreator.inputSourceSet.get()
    val sourceSetName = inputSourceSet.name
    val capitalized = sourceSetName.replaceFirstChar { it.titlecase() }

    configurations.maybeCreate("kodexCacheElementsFor$capitalized") {
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes.attribute(KODEX_USAGE_ATTRIBUTE, KODEX_CACHE_USAGE)
        attributes.attribute(KODEX_SOURCE_SET_ATTRIBUTE, sourceSetName)
        outgoing.artifact(task.outputCacheFile) { it.builtBy(task) }
    }

    val sourceDirs: List<File> = inputSourceSet.kotlin.sourceDirectories.files.filter { it.exists() }
    configurations.maybeCreate("kodexContextualSourcesElementsFor$capitalized") {
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes.attribute(KODEX_USAGE_ATTRIBUTE, KODEX_CONTEXTUAL_SOURCES_USAGE)
        attributes.attribute(KODEX_SOURCE_SET_ATTRIBUTE, sourceSetName)
        sourceDirs.forEach { dir -> outgoing.artifact(dir) }
    }
}

/**
 * Consumer side.
 *
 * For every source set that [taskCreator] declared as a contextual source set from another project
 * (via [KodexSourceSetTaskBuilder.contextualSourceSet]), creates resolvable configurations that pull
 * the producer's output cache and source directories over the project boundary and wires them into
 * [consumer]. The output cache's artifact carries the producer task as a dependency, so no explicit
 * `dependsOn` is needed.
 */
internal fun Project.wireCrossModuleContextualCaches(consumer: RunKodexTask, taskCreator: KodexSourceSetTaskBuilder) {
    val refs = taskCreator.contextualSourceSetsFromOtherProjects.get()
    if (refs.isEmpty()) return

    val dependencyHandler = dependencies
    val consumerName = taskCreator.newSourceSetName.get()

    for ((projectPath, sourceSetName) in refs) {
        val suffix = "${consumerName}_${projectPath.replace(":", "_")}_$sourceSetName"

        val cacheConf = configurations.maybeCreate("kodexInputCache_$suffix") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes.attribute(KODEX_USAGE_ATTRIBUTE, KODEX_CACHE_USAGE)
            attributes.attribute(KODEX_SOURCE_SET_ATTRIBUTE, sourceSetName)
            dependencies.add(dependencyHandler.project(mapOf("path" to projectPath)))
        }

        val sourcesConf = configurations.maybeCreate("kodexContextualSources_$suffix") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes.attribute(KODEX_USAGE_ATTRIBUTE, KODEX_CONTEXTUAL_SOURCES_USAGE)
            attributes.attribute(KODEX_SOURCE_SET_ATTRIBUTE, sourceSetName)
            dependencies.add(dependencyHandler.project(mapOf("path" to projectPath)))
        }

        // `from(Configuration)` keeps the resolution lazy and carries the producer task as a dependency.
        consumer.inputCacheFiles.from(cacheConf)
        // Contextual source roots have no producing task, so a lazily resolved file list is enough.
        consumer.contextualSources.add(provider { sourcesConf.files.toList() })
    }
}
