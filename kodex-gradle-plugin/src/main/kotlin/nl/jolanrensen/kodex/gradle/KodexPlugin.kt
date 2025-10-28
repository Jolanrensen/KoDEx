@file:Suppress("unused", "RedundantVisibilityModifier")

package nl.jolanrensen.kodex.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.JAR_TYPE
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.HasKotlinDependencies
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMetadataTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KLIB_TYPE
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.wasmDecamelizedDefaultNameOrNull
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly

/**
 * Gradle plugin part of the doc-processor project.
 *
 * Extension functions in this file enable users to more easily create a [RunKodexTask] in their build.gradle.kts
 * file.
 */
class KodexPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit =
        with(project) {
            // add maven central to repositories, which is needed to add dokka as a dependency in RunKodexTasks
            repositories.mavenCentral()

            val objects = objects
            val extension: KodexExtension = objects.newInstance(KodexExtension::class.java)
            extensions.add("kodex", extension)

            val kotlinExtension = extensions["kotlin"]
                .let { it as? KotlinProjectExtension }
                ?: error("Kotlin extension not found")

            val kotlinSourceSets = kotlinExtension.sourceSets

            afterEvaluate {
                extension.taskCreators.forEach {
                    configureRunKodexTasks(it, kotlinSourceSets, kotlinExtension)
                }
            }
        }

    private fun Project.configureRunKodexTasks(
        taskCreator: KodexSourceSetTaskBuilder,
        kotlinSourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
        kotlinExtension: KotlinProjectExtension,
    ) {
        val inputSourceSet = taskCreator.inputSourceSet.get()
        val sourceSetName = taskCreator.newSourceSetName.get()
        val taskName = taskCreator.taskName.get()

        val task = tasks.createRunKodexTask(
            name = taskName,
            sources = inputSourceSet.kotlin.sourceDirectories,
        ) {
            group = "KoDEx"
            description = "Runs KoDEx $sourceSetName on the ${inputSourceSet.name} sources"
            applyPropertiesFrom(taskCreator)
            taskCreator.runOnTask.get().forEach {
                it.execute(this)
            }
        }

        val kodexSourceSet = kotlinSourceSets.maybeCreate(sourceSetName) {
            kotlin.setSrcDirs(task.calculateTargets())
            resources.setSrcDirs(inputSourceSet.resources.sourceDirectories)
            inputSourceSet.copyDependenciesTo(this, this@configureRunKodexTasks)
        }

        try {
            // TODO
            createCompilationsAndJarTasks(
                taskCreator = taskCreator,
                kotlinExtension = kotlinExtension,
                inputSourceSet = inputSourceSet,
                sourceSetName = sourceSetName,
                task = task,
                kodexSourceSet = kodexSourceSet,
            )
        } catch (e: Exception) {
            logger.warn("Failed to create compilations and jar tasks for $sourceSetName", e)
        }
    }

    private fun Project.createCompilationsAndJarTasks(
        taskCreator: KodexSourceSetTaskBuilder,
        kotlinExtension: KotlinProjectExtension,
        inputSourceSet: KotlinSourceSet,
        sourceSetName: String,
        task: RunKodexTask,
        kodexSourceSet: KotlinSourceSet,
    ) {
        val isMain = taskCreator.isMainSourceSet.get()

        val targetsOfInputSourceSet = kotlinExtension.targets.filter { target ->
            // TODO temp turned off for multiplatform
            target !is KotlinMetadataTarget &&
                target.compilations.any { compilation ->
                    inputSourceSet in compilation.kotlinSourceSets
                }
        }
        val isSingleTarget = kotlinExtension is KotlinSingleTargetExtension<*>

        val compilationsOfKodexSourceSet = targetsOfInputSourceSet.associateWith { target ->
            val compilationName =
                if (isSingleTarget) {
                    sourceSetName
                } else {
                    "${sourceSetName}${target.name.replaceFirstChar { it.titlecase() }}"
                }
            target.compilations.create(compilationName) {
                it.compileTaskProvider.get().dependsOn(task)
                // TODO does it need more setup?
            }
        }

        when (kotlinExtension) {
            is KotlinMultiplatformExtension -> {
                if (taskCreator.generateJar.get()) {
                    error(
                        "KoDEx cannot yet generate jars for multiplatform projects. Turn off generateJar and try it yourself.",
                    )
                }

                if (taskCreator.generateSourcesJar.get()) {
                    error(
                        "KoDEx cannot yet generate sources jars for multiplatform projects. Turn off generateSourcesJar and try it yourself.",
                    )
                }
            }

            is KotlinSingleTargetExtension<*> -> {
                if (taskCreator.generateJar.get()) {
                    createJarTaskSingleTarget(
                        kotlinExtension = kotlinExtension,
                        compilationsOfKodexSourceSet = compilationsOfKodexSourceSet,
                        sourceSetName = sourceSetName,
                        isMain = isMain,
                        kodexSourceSet = kodexSourceSet,
                    )
                }
                if (taskCreator.generateSourcesJar.get()) {
                    createSourcesJarTaskSingleTarget(
                        sourceSetName = sourceSetName,
                        isMain = isMain,
                        kodexSourceSet = kodexSourceSet,
                        task = task,
                    )
                }
            }
        }
    }

    /**
     * todo
     * [org.jetbrains.kotlin.gradle.plugin.mpp.sourcesJarTask]
     * [org.jetbrains.kotlin.gradle.plugin.mpp.compilationImpl.KotlinCreateSourcesJarTaskSideEffect]
     */
    private fun Project.createSourcesJarTaskSingleTarget(
        sourceSetName: String,
        isMain: Boolean,
        kodexSourceSet: KotlinSourceSet,
        task: RunKodexTask,
    ) {
        val sourcesJar = tasks.create<Jar>(
            name = buildString {
                append("kodex")
                append(sourceSetName.replaceFirstChar { it.titlecase() })
                append("SourcesJar")
            },
        ) {
            group = "build"
            description =
                "Creates a jar file with the KoDEx processed sources of $sourceSetName"
            archiveBaseName.set(project.name)
            archiveVersion.set(project.version.toString())
            if (isMain) {
                archiveClassifier.set("kodex-sources")
            } else {
                archiveClassifier.set("kodex-${sourceSetName.decamelize()}-sources")
            }
            from(kodexSourceSet.kotlin)
            dependsOn(task)
        }
        tasks.filter {
            it != sourcesJar && "sourcesjar" in it.name.lowercase()
        }.forEach { it.dependsOn(sourcesJar) }
    }

    private fun Project.createJarTaskSingleTarget(
        kotlinExtension: KotlinSingleTargetExtension<*>,
        compilationsOfKodexSourceSet: Map<KotlinTarget, KotlinCompilation<*>>,
        sourceSetName: String,
        isMain: Boolean,
        kodexSourceSet: KotlinSourceSet,
    ) {
        val target = kotlinExtension.target
        val compilation = compilationsOfKodexSourceSet[target]
            ?: error("Compilation of $target not found, we only have: $compilationsOfKodexSourceSet")

        when (target) {
            is KotlinWithJavaTarget<*, *>, is KotlinJvmTarget -> {
                val jar = target.createArtifactsTask(sourceSetName, isMain) {
                    from(compilation.output.allOutputs)
                    dependsOn(compilation.compileTaskProvider)
                }
                target.createPublishArtifact(
                    artifactTask = jar,
                    artifactType = JAR_TYPE,
                    configurations[kodexSourceSet.apiConfigurationName],
                    configurations.findByName(kodexSourceSet.runtimeOnlyConfigurationName),
                )
            }

            is KotlinJsIrTarget -> { // TODO test
                val jsKlibTask = target.createArtifactsTask(sourceSetName, isMain) {
                    from(compilation.output.allOutputs)
                    archiveExtension.set(KLIB_TYPE)
                    //                                destinationDirectory.set(target.project.libsDirectory)
                    destinationDirectory.set(layout.buildDirectory.dir("libs"))

                    if (target.platformType == KotlinPlatformType.wasm) {
                        if (target.wasmDecamelizedDefaultNameOrNull() != null) {
                            target.disambiguationClassifier?.let { classifier ->
                                archiveAppendix.set(classifier.decamelize())
                            }
                        }
                    }
                }

                target.createPublishArtifact(
                    artifactTask = jsKlibTask,
                    KLIB_TYPE,
                    configurations[kodexSourceSet.apiConfigurationName],
                    configurations.findByName(kodexSourceSet.runtimeOnlyConfigurationName),
                )
            }

            is KotlinNativeTarget ->
                TODO(
                    "Unsupported single target $target for now, turn off generateJar and try generating a jar manually.",
                )

            else ->
                error("Unsupported target $target, turn off generateJar and try generating a jar manually")
        }
    }
}

internal fun String.decamelize(): String =
    replace("([A-Z])".toRegex()) {
        val (first) = it.destructured
        "-${first.toLowerCaseAsciiOnly()}"
    }

internal fun KotlinTarget.createArtifactsTask(
    sourceSetName: String,
    isMain: Boolean,
    configure: Jar.() -> Unit = {},
): TaskProvider<Jar> =
    project.tasks.register<Jar>(
        name = buildString {
            append("kodex")
            append(sourceSetName.replaceFirstChar { it.titlecase() })
            append(artifactsTaskName.replaceFirstChar { it.titlecase() })
        },
    ) {
        description = "Assembles an archive containing the main classes processed by KoDEx."
        group = BasePlugin.BUILD_GROUP
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true

        archiveBaseName.set(project.name)
        archiveVersion.set(project.version.toString())
        if (isMain) {
            archiveClassifier.set("kodex")
        } else {
            archiveClassifier.set("kodex-${sourceSetName.decamelize()}")
        }

        disambiguationClassifier?.let { classifier ->
            archiveAppendix.set(classifier.toLowerCaseAsciiOnly())
        }

        configure()
    }

internal fun KotlinTarget.createPublishArtifact(
    artifactTask: TaskProvider<*>,
    artifactType: String,
    vararg elementsConfiguration: Configuration?,
): PublishArtifact {
    val artifact = project.artifacts.add(ARCHIVES_CONFIGURATION, artifactTask) { artifact ->
        artifact.builtBy(artifactTask)
        artifact.type = artifactType
    }

    elementsConfiguration.filterNotNull().forEach { configuration ->
        configuration.outgoing.artifacts.add(artifact)
        // configuration.outgoing.attributes.setAttribute(project.artifactTypeAttribute, artifactType)
    }

    return artifact
}

val KotlinProjectExtension.targets: Iterable<KotlinTarget>
    get() = when (this) {
        is KotlinSingleTargetExtension<*> -> listOf(this.target)
        is KotlinMultiplatformExtension -> targets
        else -> error("Unexpected 'kotlin' extension $this")
    }

fun KotlinSourceSet.copyDependenciesTo(other: KotlinSourceSet, project: Project) {
    val configurations = listOf(
        HasKotlinDependencies::implementationConfigurationName,
        HasKotlinDependencies::runtimeOnlyConfigurationName,
        HasKotlinDependencies::apiConfigurationName,
        HasKotlinDependencies::compileOnlyConfigurationName,
    )

    for (config in configurations) {
        project.configurations[config(other)]
            .extendsFrom(project.configurations[config(this)])
    }
}
