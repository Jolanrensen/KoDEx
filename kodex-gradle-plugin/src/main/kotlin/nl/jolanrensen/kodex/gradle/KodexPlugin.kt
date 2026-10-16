@file:Suppress("unused", "RedundantVisibilityModifier")

package nl.jolanrensen.kodex.gradle

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency.ARCHIVES_CONFIGURATION
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.attributes.Attribute
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.hasPlugin
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
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
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
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
            val objects = objects
            val extension: KodexExtension = objects.newInstance(KodexExtension::class.java)
            extensions.add("kodex", extension)

            val kotlinExtension = extensions["kotlin"]
                .let { it as? KotlinProjectExtension }
                ?: error("Kotlin extension not found")

            val kotlinSourceSets = kotlinExtension.sourceSets

            val preprocessAll = project.tasks.register("preprocessAllWithKodex") {
                it.group = "KoDEx"
                it.description = "Runs KoDEx on all subprojects"
            }
            project.subprojects { subProject ->
                plugins.withType<KodexPlugin> {
                    subProject.tasks.withType<RunKodexTask>().configureEach { task ->
                        preprocessAll.configure {
                            it.dependsOn(task)
                        }
                    }
                }
            }

            afterEvaluate {
                // pass 1: create a task for every registered source set
                val taskBySourceSet =
                    extension.taskCreators.associate { taskCreator ->
                        taskCreator.inputSourceSet.get() to
                            configureRunKodexTasks(taskCreator, kotlinSourceSets, kotlinExtension)
                    }

                // pass 2: for each task, connect the output cache of every contextual source set
                // that is produced locally to this task's input cache, so its documentables can be
                // carried over instead of re-analysed. (Cross-module contextual source sets are not
                // in this map and are handled separately.)
                extension.taskCreators.forEach { taskCreator ->
                    val consumer = taskBySourceSet[taskCreator.inputSourceSet.get()]!!
                    for (contextualSourceSet in taskCreator.contextualSourceSets.get()) {
                        val producer = taskBySourceSet[contextualSourceSet] ?: continue
                        consumer.inputCacheFiles.from(producer.outputCacheFile)
                        consumer.dependsOn(producer)
                    }

                    // contextual source sets coming from other modules
                    wireCrossModuleContextualCaches(consumer, taskCreator)
                }
            }
        }

    private fun Project.configureRunKodexTasks(
        taskCreator: KodexSourceSetTaskBuilder,
        kotlinSourceSets: NamedDomainObjectContainer<KotlinSourceSet>,
        kotlinExtension: KotlinProjectExtension,
    ): RunKodexTask {
        val inputSourceSet = taskCreator.inputSourceSet.get()
        val contextualSourceSets = taskCreator.contextualSourceSets.get()
        val sourceSetName = taskCreator.newSourceSetName.get()
        val taskName = taskCreator.taskName.get()

        val task = taskCreator.task.get().apply {
            check(name == taskName) {
                "The task name of KoDEx task '$name' cannot be changed after `kodex.preprocess()` is called."
            }
            sources.set(inputSourceSet.kotlin.sourceDirectories)
            group = "KoDEx"
            description = "Runs KoDEx $sourceSetName on the ${inputSourceSet.name} sources"
            contextualSources.set(
                contextualSourceSets.map { it.kotlin.sourceDirectories.toList() },
            )
            applyPropertiesFrom(taskCreator)
            taskCreator.runOnTask.get().forEach {
                it.execute(this)
            }
        }

        createCompilationsSourceSetsAndJarTasks(
            taskCreator = taskCreator,
            kotlinExtension = kotlinExtension,
            inputSourceSet = inputSourceSet,
            sourceSetName = sourceSetName,
            task = task,
        )

        // expose this task's output cache and sources so other modules can use it as a contextual source set
        exposeContextualCaches(taskCreator, task)

        return task
    }

    private fun Project.createCompilationsSourceSetsAndJarTasks(
        taskCreator: KodexSourceSetTaskBuilder,
        kotlinExtension: KotlinProjectExtension,
        inputSourceSet: KotlinSourceSet,
        sourceSetName: String,
        task: RunKodexTask,
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

        // the original compilation of inputSourceSet per target
        val originalCompilations = targetsOfInputSourceSet.associateWith { target ->
            target.compilations.firstOrNull { it.defaultSourceSet == inputSourceSet }
                ?: target.compilations.first { inputSourceSet in it.kotlinSourceSets }
        }

        val compilationsOfKodexSourceSet = targetsOfInputSourceSet.associateWith { target ->
            val compilationName =
                if (isSingleTarget) {
                    sourceSetName
                } else {
                    "${sourceSetName}${target.name.replaceFirstChar { it.titlecase() }}"
                }
            val originalCompilation = originalCompilations[target]!!

            target.compilations.create(compilationName) {
                // creates kodexSourceSet
                it.defaultSourceSet {
                    kotlin.setSrcDirs(task.calculateTargets())
                    resources.setSrcDirs(inputSourceSet.resources.sourceDirectories)
                    inputSourceSet.copyDependenciesTo(this, this@createCompilationsSourceSetsAndJarTasks)
                }
                it.compileTaskProvider.configure { compileTask ->
                    compileTask.dependsOn(task)
                    // compile the KoDEx sources exactly like the original sources
                    compileTask.copyCompilerOptionsFrom(originalCompilation.compileTaskProvider.get())
                }
            }.also { kodexCompilation ->
                configureJavaSourceSetOfKodexCompilation(
                    task = task,
                    originalCompilation = originalCompilation,
                    kodexCompilation = kodexCompilation,
                )
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
                val target = kotlinExtension.target
                val kodexCompilation = compilationsOfKodexSourceSet[target]!!
                val originalCompilation = originalCompilations[target]!!
                val kodexSourceSet = kodexCompilation.defaultSourceSet

                val jar = if (taskCreator.generateJar.get()) {
                    createJarTaskSingleTarget(
                        kotlinExtension = kotlinExtension,
                        compilationsOfKodexSourceSet = compilationsOfKodexSourceSet,
                        originalCompilation = originalCompilation,
                        sourceSetName = sourceSetName,
                        isMain = isMain,
                        kodexSourceSet = kodexSourceSet,
                    )
                } else {
                    null
                }
                val sourcesJar = if (taskCreator.generateSourcesJar.get()) {
                    createSourcesJarTaskSingleTarget(
                        sourceSetName = sourceSetName,
                        isMain = isMain,
                        kodexSourceSet = kodexSourceSet,
                        kodexCompilation = kodexCompilation,
                        task = task,
                    )
                } else {
                    null
                }

                if (taskCreator.addJarsToAssemble.get()) {
                    tasks.named(BasePlugin.ASSEMBLE_TASK_NAME) {
                        if (jar != null) it.dependsOn(jar)
                        if (sourcesJar != null) it.dependsOn(sourcesJar)
                    }
                }

                if (taskCreator.replaceArtifactsInJavaComponent.get()) {
                    check(isMain && (target is KotlinJvmTarget || target is KotlinWithJavaTarget<*, *>)) {
                        "`replaceArtifactsInJavaComponent` is only supported for the main source set of a JVM project."
                    }
                    // Run as late as possible, as some plugins only call `java.withSourcesJar()` in `afterEvaluate {}`
                    gradle.projectsEvaluated {
                        replaceArtifactsInJavaComponent(
                            originalCompilation = originalCompilation,
                            jar = jar,
                            sourcesJar = sourcesJar,
                        )
                    }
                }
            }
        }
    }

    /**
     * The Java source set of [kodexCompilation] (if it exists) gets the same source directories as the
     * Java source set of [originalCompilation], but with any directory processed by KoDEx
     * replaced by its processed counterpart.
     * This makes sure Java sources are compiled too.
     */
    private fun Project.configureJavaSourceSetOfKodexCompilation(
        task: RunKodexTask,
        originalCompilation: KotlinCompilation<*>,
        kodexCompilation: KotlinCompilation<*>,
    ) {
        val javaSourceSets = extensions.findByType(SourceSetContainer::class.java) ?: return
        val originalJavaSourceSet = javaSourceSets.findByName(originalCompilation.name) ?: return
        val kodexJavaSourceSet = javaSourceSets.findByName(kodexCompilation.name) ?: return

        val processedSources = task.sources.get().map { it.absoluteFile }.toSet()
        kodexJavaSourceSet.java.setSrcDirs(
            originalJavaSourceSet.java.srcDirs.map {
                if (it.absoluteFile in processedSources) task.calculateTarget(it) else it
            },
        )
        tasks.named(kodexJavaSourceSet.compileJavaTaskName) { it.dependsOn(task) }
    }

    /**
     * Replaces the jar and sources jar variants of the `java` component with variants containing the
     * KoDEx-processed [jar] and [sourcesJar].
     * The attributes, dependencies, and capabilities of the original variants are kept.
     *
     * The new configurations are not consumable, so they are only used for publishing.
     *
     * NOTE: Delicate API, see [KodexSourceSetTaskBuilder.replaceArtifactsInJavaComponent].
     * This relies on how Gradle builds the `java` component and may break in the future.
     */
    private fun Project.replaceArtifactsInJavaComponent(
        originalCompilation: KotlinCompilation<*>,
        jar: TaskProvider<Jar>?,
        sourcesJar: TaskProvider<Jar>?,
    ) {
        val component = components.findByName("java") as? AdhocComponentWithVariants
        if (component == null) {
            logger.warn("KoDEx: Could not find the 'java' component in $path, its artifacts will not be replaced.")
            return
        }

        fun replaceVariant(originalConfigurationName: String, artifactTask: TaskProvider<Jar>, classifier: String?) {
            val original = configurations.findByName(originalConfigurationName) ?: return
            val replacement = configurations.maybeCreate(
                "kodex${originalConfigurationName.replaceFirstChar { it.titlecase() }}",
            ) {
                description = "KoDEx-processed version of '$originalConfigurationName', used for publishing."
                isCanBeConsumed = false
                isCanBeResolved = false
                extendsFrom(*original.extendsFrom.toTypedArray())
                for (key in original.attributes.keySet()) {
                    @Suppress("UNCHECKED_CAST")
                    attributes.attributeProvider(
                        key as Attribute<Any>,
                        provider { original.attributes.getAttribute(key)!! },
                    )
                }
                original.outgoing.capabilities.forEach { outgoing.capability(it) }
                outgoing.artifact(artifactTask) {
                    it.builtBy(artifactTask)
                    // publish with the same classifier as the original artifact
                    it.classifier = classifier
                }
            }

            component.withVariantsFromConfiguration(original) { it.skip() }
            component.addVariantsFromConfiguration(replacement) {
                // same mapping as the `java` component does
                when (originalConfigurationName) {
                    JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME -> it.mapToMavenScope("compile")
                    JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME -> it.mapToMavenScope("runtime")
                    else -> it.mapToOptional()
                }
            }
            logger.info("KoDEx: Replaced variant '$originalConfigurationName' of the 'java' component in $path.")
        }

        if (jar != null) {
            replaceVariant(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME, jar, null)
            replaceVariant(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME, jar, null)
        }
        if (sourcesJar != null) {
            val sourcesElementsName = extensions.findByType(SourceSetContainer::class.java)
                ?.findByName(originalCompilation.name)
                ?.sourcesElementsConfigurationName
                ?: "sourcesElements"
            replaceVariant(sourcesElementsName, sourcesJar, "sources")
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
        kodexCompilation: KotlinCompilation<*>,
        task: RunKodexTask,
    ): TaskProvider<Jar> {
        val kodexJavaSourceSet = extensions.findByType(SourceSetContainer::class.java)
            ?.findByName(kodexCompilation.name)

        return tasks.register<Jar>(
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
            // like the normal sources jar, which contains `allSource`: Kotlin and Java sources plus resources
            from(kodexSourceSet.kotlin)
            kodexJavaSourceSet?.let { from(it.allSource) }
            from(kodexSourceSet.resources)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            dependsOn(task)
        }
    }

    private fun Project.createJarTaskSingleTarget(
        kotlinExtension: KotlinSingleTargetExtension<*>,
        compilationsOfKodexSourceSet: Map<KotlinTarget, KotlinCompilation<*>>,
        originalCompilation: KotlinCompilation<*>,
        sourceSetName: String,
        isMain: Boolean,
        kodexSourceSet: KotlinSourceSet,
    ): TaskProvider<Jar> {
        val target = kotlinExtension.target
        val compilation = compilationsOfKodexSourceSet[target]
            ?: error("Compilation of $target not found, we only have: $compilationsOfKodexSourceSet")

        return when (target) {
            is KotlinWithJavaTarget<*, *>, is KotlinJvmTarget -> {
                val originalJavaSourceSet = extensions.findByType(SourceSetContainer::class.java)
                    ?.findByName(originalCompilation.name)
                val originalJar = if (isMain) tasks.findByName(target.artifactsTaskName) as? Jar else null

                target.createArtifactsTask(sourceSetName, isMain) {
                    // the classes compiled from the KoDEx-processed sources
                    from(compilation.output.classesDirs)
                    dependsOn(compilation.compileTaskProvider)

                    if (originalJavaSourceSet != null) {
                        // the resources as processed for the original sources,
                        // keeping any modifications made to the original processResources task
                        from(tasks.named(originalJavaSourceSet.processResourcesTaskName))
                    } else {
                        from(compilation.output.resourcesDir)
                        dependsOn(compilation.output.allOutputs)
                    }

                    // keep the same manifest as the original jar
                    if (originalJar != null) manifest.from(originalJar.manifest)
                }
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
                jsKlibTask
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

/**
 * Copies the compiler options of the [original] compilation task to this task, so that
 * a KoDEx-processed source set is compiled exactly like the original source set.
 *
 * Notably, this copies the `moduleName`, so that the names of `internal` declarations
 * (and the `.kotlin_module` file) stay the same as in the original compilation.
 */
internal fun KotlinCompilationTask<*>.copyCompilerOptionsFrom(original: KotlinCompilationTask<*>) {
    val from = original.compilerOptions
    val to = compilerOptions

    to.freeCompilerArgs.set(from.freeCompilerArgs)
    to.optIn.set(from.optIn)
    to.languageVersion.set(from.languageVersion)
    to.apiVersion.set(from.apiVersion)
    to.progressiveMode.set(from.progressiveMode)
    to.allWarningsAsErrors.set(from.allWarningsAsErrors)
    to.suppressWarnings.set(from.suppressWarnings)

    if (from is KotlinJvmCompilerOptions && to is KotlinJvmCompilerOptions) {
        to.moduleName.set(from.moduleName)
        to.jvmTarget.set(from.jvmTarget)
        to.javaParameters.set(from.javaParameters)
        to.noJdk.set(from.noJdk)
    }

    if (this is BaseKotlinCompile && original is BaseKotlinCompile) {
        friendPaths.from(original.friendPaths)
    }
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
