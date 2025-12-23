package nl.jolanrensen.kodex.gradle

import io.github.oshai.kotlinlogging.KotlinLogging
import nl.jolanrensen.kodex.RunKodexAction
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

private val log = KotlinLogging.logger { }

/**
 * Process doc task you can instantiate in your build.gradle(.kts) file.
 * For example using [Project.creatingProcessDocTask].
 */
abstract class RunKodexTask
    @Inject
    constructor(
        @get:Internal val factory: ObjectFactory,
        project: Project,
    ) : DefaultTask(),
        CommonKodexTaskProperties {

        init {
            applyConventions(project, factory, taskIdentity.name)
        }

        /** Source root folders for preprocessing. This needs to be set! */
        @get:InputFiles
        abstract val sources: ListProperty<File>

        /** Source root folders for preprocessing. This needs to be set! */
        fun sources(files: Iterable<File>): Unit = sources.set(files)

        /**
         * Set base directory which will be used for relative source paths.
         * By default, it is '$projectDir'.
         */
        @get:Input
        val baseDir: Property<File> = factory
            .property<File>()
            .convention(project.projectDir)

        /**
         * Set base directory which will be used for relative source paths.
         * By default, it is '$projectDir'.
         */
        fun baseDir(file: File): Unit = baseDir.set(file)

        /**
         * Where the generated sources are placed.
         *
         * Only readable after the task has been executed.
         * @see calculateTargets
         */
        @get:OutputFiles
        val targets: FileCollection = factory.fileCollection()

        /**
         * Calculate the targets based on the [sources] and [target] folder.
         */
        fun calculateTargets(): FileCollection {
            val relativeSources = sources.get().map { it.relativeTo(baseDir.get()) }
            val target = target.get()
            return factory.fileCollection().from(relativeSources.map { File(target, it.path) })
        }

        /** Used by the task to execute [RunKodexGradleAction]. */
        @get:Inject
        abstract val workerExecutor: WorkerExecutor

        @get:InputFiles
        internal val projectClassPath: ListProperty<File> = factory
            .listProperty<File>()
            .convention(project.files().toList())

        @Deprecated(
            "This notation is no longer needed.",
            replaceWith = ReplaceWith("block()"),
            level = DeprecationLevel.WARNING,
        )
        fun task(block: RunKodexTask.() -> Unit) {
            block()
        }

        init {
            outputs.upToDateWhen {
                target.get().let {
                    it.exists() && it.listFiles()?.isNotEmpty() == true
                }
            }
        }

        @TaskAction
        fun process() {
            // redirect println to INFO logs
            logging.captureStandardOutput(LogLevel.INFO)

            // redirect System.err to ERROR logs
            logging.captureStandardError(LogLevel.ERROR)

            log.lifecycle { "Kodex is running!" }

            val sourceRoots = sources.get()
            val target = target.get()
            val runtime = classpath.get() // .resolve()
            val processors = processors.get()

            (targets as ConfigurableFileCollection).setFrom(calculateTargets())

            if (target.exists()) target.deleteRecursively()
            target.mkdir()

            log.info { "Using target folder: $target" }
            log.info { "Using source folders: $sourceRoots" }
            log.info { "Using target folders: ${targets.files.toList()}" }
            log.info { "Using runtime classpath: ${runtime.joinToString("\n")}" }

            val sourceSetName = "sourceSet"
            val sourceSetSpec = RunKodexAction.SourceSetSpec(
                name = sourceSetName,
                sourceRoots = sourceRoots.filter { it.exists() },
                languageVersion = languageVersion.getOrNull(),
                apiVersion = apiVersion.getOrNull(),
                analysisPlatform = analysisPlatform.getOrNull(),
                classpath = projectClassPath.get(),
            )

            val workQueue = workerExecutor.classLoaderIsolation {
                it.classpath.setFrom(runtime)
            }

            workQueue.submit(RunKodexGradleAction::class.java) {
                it.baseDir = baseDir.get()
                it.sources = sourceSetSpec
                it.sourceRoots = sourceRoots
                it.target = target
                it.processors = processors
                it.processLimit = processLimit.get()
                it.arguments = arguments.get()
                it.exportAsHtmlDir = exportAsHtml.get().dir.get()
                it.outputReadOnly = outputReadOnly.get()
                it.htmlOutputReadOnly = exportAsHtml.get().outputReadOnly.get()
            }
        }
    }
