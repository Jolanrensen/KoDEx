package nl.jolanrensen.kodex.gradle

import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.COMMENT_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.EXPORT_AS_HTML_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_FILE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.SAMPLE_DOC_PROCESSOR
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import java.io.File

interface CommonKodexTaskProperties {

    /**
     * Target folder to place the preprocessing results in.
     */
    @get:Input
    val target: Property<File>

    /**
     * Target folder to place the preprocessing results in.
     */
    fun target(file: File): Unit = target.set(file)

    /**
     * Whether the output at [target] should be read-only.
     * Defaults to `true`.
     */
    @get:Input
    val outputReadOnly: Property<Boolean>

    /**
     * Whether the output at [target] should be read-only.
     * Defaults to `true`.
     */
    fun outputReadOnly(boolean: Boolean): Unit = outputReadOnly.set(boolean)

    /**
     * The limit for while-true loops in processors. This is to prevent infinite loops.
     */
    @get:Input
    val processLimit: Property<Int>

    /**
     * The limit for while-true loops in processors. This is to prevent infinite loops.
     */
    fun processLimit(int: Int): Unit = processLimit.set(int)

    /**
     * The processors to use. These must be fully qualified names, such as:
     * `"com.example.plugin.MyProcessor"`
     *
     * Defaults to:
     * [[COMMENT_DOC_PROCESSOR],
     *  [INCLUDE_DOC_PROCESSOR],
     *  [INCLUDE_FILE_DOC_PROCESSOR],
     *  [ARG_DOC_PROCESSOR],
     *  [SAMPLE_DOC_PROCESSOR],
     *  [EXPORT_AS_HTML_DOC_PROCESSOR],
     *  [REMOVE_ESCAPE_CHARS_PROCESSOR]]
     */
    @get:Input
    val processors: ListProperty<String>

    /**
     * The processors to use. These must be fully qualified names, such as:
     * `"com.example.plugin.MyProcessor"`
     */
    fun processors(vararg strings: String): Unit = processors.set(strings.toList())

    /**
     * The arguments to be passed on to the processors.
     */
    @get:Input
    val arguments: MapProperty<String, Any>

    /**
     * The arguments to be passed on to the processors.
     */
    fun arguments(map: Map<String, Any>): Unit = arguments.set(map)

    /**
     * The arguments to be passed on to the processors.
     */
    fun arguments(vararg arguments: Pair<String, Any>): Unit = this.arguments.set(arguments.toMap())

    /** The classpath of this task. */
    @get:Classpath
    val classpath: Property<Configuration>

    @get:Nested
    val exportAsHtml: Property<ExportAsHtmlDsl>

    /**
     * DSL for configuring the @ExportAsHtml task.
     */
    fun exportAsHtml(action: Action<ExportAsHtmlDsl>): Unit = action.execute(exportAsHtml.get())

    /**
     * DSL to add plugin dependencies to the current task. If you want to include a processor from an external library,
     * that library needs to be added to the classpath of this task using this DSL.
     *
     * For example:
     *
     * ```groovy
     * dependencies.plugin("com.example.plugin:my-doc-processor-plugin:1.4.32")
     * ```
     */
    @get:Internal
    val dependencies: Property<DependencySetPluginDsl>

    /**
     * DSL to add plugin dependencies to the current task. If you want to include a processor from an external library,
     * that library needs to be added to the classpath of this task using this DSL.
     *
     * For example:
     *
     * ```groovy
     * dependencies {
     *     plugin "com.example.plugin:my-doc-processor-plugin:1.4.32"
     * }
     * ```
     */
    fun dependencies(action: Action<DependencySetPluginDsl>): Unit = action.execute(dependencies.get())
}

abstract class ExportAsHtmlDsl {

    /**
     * Target folder of @ExportAsHtml Docs
     *
     * Defaults to $target/htmlExports
     */
    @get:Input
    abstract val dir: Property<File>

    /**
     * Target folder of @ExportAsHtml Docs
     *
     * Defaults to $target/htmlExports
     */
    fun dir(file: File): Unit = dir.set(file)

    /**
     * Whether the output at [dir] should be read-only.
     * Defaults to `true`.
     */
    @get:Input
    abstract val outputReadOnly: Property<Boolean>

    /**
     * Whether the output at [dir] should be read-only.
     * Defaults to `true`.
     */
    fun outputReadOnly(boolean: Boolean): Unit = outputReadOnly.set(boolean)
}

interface DependencySetPluginDsl {

    /**
     * Adds a plugin dependency to the classpath of this task.
     * Don't forget to add any new processor to the [CommonKodexTaskProperties.processors] list.
     *
     * @param dependencyNotation Dependency notation
     */
    fun plugin(dependencyNotation: Any)
}

fun CommonKodexTaskProperties.applyPropertiesFrom(other: CommonKodexTaskProperties) {
    target.set(other.target)
    outputReadOnly.set(other.outputReadOnly)
    processLimit.set(other.processLimit)
    processors.set(other.processors)
    arguments.set(other.arguments)
    classpath.set(other.classpath)

    val otherExportAsHtml = other.exportAsHtml.get()
    if (otherExportAsHtml.dir.isPresent) {
        exportAsHtml.get().dir.set(otherExportAsHtml.dir)
    }
    if (otherExportAsHtml.outputReadOnly.isPresent) {
        exportAsHtml.get().outputReadOnly.set(otherExportAsHtml.outputReadOnly)
    }
}

fun CommonKodexTaskProperties.applyConventions(project: Project, factory: ObjectFactory, folderName: String) {
    target.convention(
        project.layout.buildDirectory
            .dir("kodex${File.separatorChar}$folderName")
            .map { it.asFile },
    )
    outputReadOnly.convention(true)
    processLimit.convention(10_000)
    processors.convention(
        listOf(
            COMMENT_DOC_PROCESSOR,
            INCLUDE_DOC_PROCESSOR,
            INCLUDE_FILE_DOC_PROCESSOR,
            ARG_DOC_PROCESSOR,
            SAMPLE_DOC_PROCESSOR,
            EXPORT_AS_HTML_DOC_PROCESSOR,
            REMOVE_ESCAPE_CHARS_PROCESSOR,
        ),
    )
    arguments.convention(emptyMap())
    classpath.convention(project.maybeCreateRuntimeConfiguration())

    val exportAsHtmlInstance = factory.newInstance(ExportAsHtmlDsl::class.java)
    exportAsHtmlInstance.dir.convention(target.map { File(it, "htmlExports") })
    exportAsHtmlInstance.outputReadOnly.convention(true)
    exportAsHtml.set(exportAsHtmlInstance)

    dependencies.set(
        object : DependencySetPluginDsl {
            /**
             * Gets the set of declared dependencies directly contained in this configuration
             * (ignoring super configurations).
             * <p>
             * This method does not resolve the configuration. Therefore, the return value does not include
             * transitive dependencies.
             *
             * @return the set of dependencies
             * @see #extendsFrom(Configuration...)
             */
            val dependencies: DependencySet
                get() = classpath.get().dependencies

            override fun plugin(dependencyNotation: Any) {
                dependencies.add(
                    project.dependencies.create(dependencyNotation),
                )
            }
        },
    )
}

internal fun Project.maybeCreateRuntimeConfiguration(): Configuration =
    project.configurations.maybeCreate("kotlinKdocIncludePluginRuntime") {
        isCanBeConsumed = false
        isCanBeResolved = true

        val dokkaVersion = "2.1.0"

        listOf(
//            "org.jetbrains.dokka:analysis-kotlin-api:$dokkaVersion",
            "org.jetbrains.dokka:analysis-kotlin-symbols:$dokkaVersion",
            "org.jetbrains.dokka:dokka-base:$dokkaVersion",
            "org.jetbrains.dokka:dokka-core:$dokkaVersion",
            "org.jetbrains.dokka:dokka-base-test-utils:$dokkaVersion",
            "org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion",
        ).forEach { dependencies += project.dependencies.create(it) }

//        resolutionStrategy {
//            it.force(
//                "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
//                "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion",
//                "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion",
//                "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion",
//                // keep compiler artifacts aligned if any transitive pulls them in
//                "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion",
//                "org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion",
//            )
//        }
    }

internal fun <T : Any> NamedDomainObjectContainer<T>.maybeCreate(name: String, configuration: T.() -> Unit): T =
    findByName(name) ?: create(name, configuration)
