package nl.jolanrensen.kodex.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Usage
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
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
 *
 * Besides the KoDEx attributes, the variants also get a custom [Usage] attribute. This makes sure
 * normal variants (like `runtimeElements`) are never selected by KoDEx consumers and KoDEx variants
 * are never selected by normal consumers.
 */
internal fun Project.exposeContextualCaches(taskCreator: KodexSourceSetTaskBuilder, task: RunKodexTask) {
    val inputSourceSet = taskCreator.inputSourceSet.get()
    val sourceSetName = inputSourceSet.name
    val capitalized = sourceSetName.replaceFirstChar { it.titlecase() }
    val objects = objects

    configurations.maybeCreate("kodexCacheElementsFor$capitalized") {
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, KODEX_CACHE_USAGE))
        attributes.attribute(KODEX_USAGE_ATTRIBUTE, KODEX_CACHE_USAGE)
        attributes.attribute(KODEX_SOURCE_SET_ATTRIBUTE, sourceSetName)
        outgoing.artifact(task.outputCacheFile) { it.builtBy(task) }
    }

    val sourceDirs: List<File> = inputSourceSet.kotlin.sourceDirectories.files.filter { it.exists() }
    configurations.maybeCreate("kodexContextualSourcesElementsFor$capitalized") {
        isCanBeConsumed = true
        isCanBeResolved = false
        attributes.attribute(
            Usage.USAGE_ATTRIBUTE,
            objects.named(Usage::class.java, KODEX_CONTEXTUAL_SOURCES_USAGE),
        )
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
 *
 * If the other project does not expose KoDEx variants for that source set (because it doesn't apply
 * KoDEx, or doesn't `preprocess` that source set), there is no cache, and the source directories of that
 * source set are read directly from the other project instead. They are then analysed as normal
 * (uncached) contextual sources.
 */
internal fun Project.wireCrossModuleContextualCaches(consumer: RunKodexTask, taskCreator: KodexSourceSetTaskBuilder) {
    val refs = taskCreator.contextualSourceSetsFromOtherProjects.get()
    if (refs.isEmpty()) return

    val dependencyHandler = dependencies
    val objects = objects
    val consumerName = taskCreator.newSourceSetName.get()

    for ((projectPath, sourceSetName) in refs) {
        val suffix = "${consumerName}_${projectPath.replace(":", "_")}_$sourceSetName"

        fun createKodexConfiguration(name: String, kodexUsage: String): Configuration =
            configurations.maybeCreate(name) {
                isCanBeConsumed = false
                isCanBeResolved = true
                // never drag in the (runtime) dependencies of the other project
                isTransitive = false
                attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, kodexUsage))
                attributes.attribute(KODEX_USAGE_ATTRIBUTE, kodexUsage)
                attributes.attribute(KODEX_SOURCE_SET_ATTRIBUTE, sourceSetName)
                dependencies.add(dependencyHandler.project(mapOf("path" to projectPath)))
            }

        val cacheConf = createKodexConfiguration("kodexInputCache_$suffix", KODEX_CACHE_USAGE)
        val sourcesConf = createKodexConfiguration("kodexContextualSources_$suffix", KODEX_CONTEXTUAL_SOURCES_USAGE)

        // The resolved artifacts provider is lazy and carries the producer task as a dependency.
        consumer.inputCacheFiles.from(cacheConf.kodexArtifactFiles(KODEX_CACHE_USAGE))

        // Fallback for when the other project does not expose KoDEx variants for this source set.
        // `provider {}` is evaluated at configuration time (after all projects are configured),
        // so it's safe to access the model of the other project here.
        val fallbackSourceDirs = provider {
            FallbackSourceDirs(sourceDirectoriesOfOtherProjectOrNull(projectPath, sourceSetName))
        }

        // Contextual source roots have no producing task, so a lazily resolved file list is enough.
        consumer.contextualSources.add(
            sourcesConf.kodexArtifactFiles(KODEX_CONTEXTUAL_SOURCES_USAGE)
                .zip(fallbackSourceDirs) { kodexSourceDirs, fallback ->
                    kodexSourceDirs.ifEmpty {
                        val dirs = fallback.dirs ?: error(
                            "KoDEx: Project '$projectPath' does not expose KoDEx source set '$sourceSetName' and " +
                                "does not have a source set named '$sourceSetName' either. " +
                                "It cannot be used as contextual source set.",
                        )
                        Logging.getLogger("KoDEx").info(
                            "KoDEx: Project '$projectPath' does not expose KoDEx source set '$sourceSetName'. " +
                                "Using its source directories $dirs directly as (uncached) contextual sources.",
                        )
                        dirs
                    }
                },
        )
    }
}

/**
 * Lazily resolves this configuration leniently (so it doesn't fail if the other project does not have
 * a matching KoDEx variant) and only returns the files coming from a variant with the given [kodexUsage].
 */
private fun Configuration.kodexArtifactFiles(kodexUsage: String): Provider<List<File>> =
    incoming
        .artifactView { it.lenient(true) }
        .artifacts
        .resolvedArtifacts
        .map { artifacts ->
            artifacts
                .filter { it.variant.attributes.getAttribute(KODEX_USAGE_ATTRIBUTE) == kodexUsage }
                .map { it.file }
        }

/** Wrapper for [sourceDirectoriesOfOtherProjectOrNull], since providers cannot contain `null`. */
private data class FallbackSourceDirs(val dirs: List<File>?) : Serializable

/**
 * Reads the (existing) source directories of the source set named [sourceSetName] of the project at
 * [projectPath] directly from that project's model, or `null` if it does not have such a source set.
 *
 * Used as a fallback when that project doesn't apply KoDEx (or doesn't preprocess that source set).
 * This should be evaluated lazily, after all projects have been configured.
 */
private fun Project.sourceDirectoriesOfOtherProjectOrNull(projectPath: String, sourceSetName: String): List<File>? {
    val other = findProject(projectPath)
        ?: error("KoDEx: Could not find project '$projectPath' for contextual source set '$sourceSetName'.")

    // Kotlin source sets (works for both JVM and multiplatform projects)
    val kotlinDirs = (other.extensions.findByName("kotlin") as? KotlinProjectExtension)
        ?.sourceSets
        ?.findByName(sourceSetName)
        ?.kotlin
        ?.sourceDirectories
        ?.files

    // Java source sets, also works when the Kotlin plugin of the other project is loaded by a different classloader
    val javaDirs = other.extensions.findByType(SourceSetContainer::class.java)
        ?.findByName(sourceSetName)
        ?.let { javaSourceSet ->
            val kotlinSourceDirectorySet = javaSourceSet.extensions.findByName("kotlin") as? SourceDirectorySet
            javaSourceSet.java.srcDirs + kotlinSourceDirectorySet?.srcDirs.orEmpty()
        }

    val dirs = kotlinDirs ?: javaDirs ?: return null

    return dirs.filter { it.exists() }.distinct()
}
