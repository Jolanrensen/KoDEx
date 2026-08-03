package nl.jolanrensen.kodex.gradle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

/**
 * How a [RunKodexTask] launches [RunKodexGradleAction].
 *
 * @see KodexWorkerIsolationDsl
 */
enum class KodexIsolationMode {

    /**
     * Run KoDEx in a separate JVM. This is the default.
     *
     * KoDEx analyses sources with Dokka, which in turn uses the Kotlin Analysis API. That machinery keeps static
     * state (an IntelliJ `Application`, `Disposer` registrations, jar file handles) that is not designed to be
     * created and torn down repeatedly, nor to be shared between threads. Running it inside the Gradle daemon
     * therefore causes two problems in multi-module builds:
     *  - `OutOfMemoryError: Metaspace`, because the per-task classloaders cannot be unloaded.
     *  - `Cannot resolve /META-INF/analysis-api/analysis-api-fir.xml`, caused by
     *    `java.io.IOException: Stream closed`. When a worker finishes, Gradle closes its isolated classloader,
     *    which closes jar file handles that a concurrently starting worker is still reading the Analysis API's
     *    plugin descriptors from. See [gradle/gradle#18305](https://github.com/gradle/gradle/issues/18305).
     *
     * A separate JVM per worker avoids both. This is also what the Dokka Gradle plugin does by default.
     */
    PROCESS,

    /**
     * Run KoDEx inside the Gradle daemon, using an isolated classloader.
     *
     * Faster, because no JVM has to be forked, but only safe when at most one KoDEx task runs at a time
     * and few KoDEx tasks run per daemon. See [PROCESS] for what can go wrong.
     */
    CLASS_LOADER,
}

/**
 * DSL for configuring how a [RunKodexTask] launches [RunKodexGradleAction].
 *
 * ```kotlin
 * workerIsolation {
 *     mode = KodexIsolationMode.PROCESS
 *     maxHeapSize = "1g"
 * }
 * ```
 *
 * @see CommonKodexTaskProperties.workerIsolation
 */
abstract class KodexWorkerIsolationDsl {

    /**
     * Whether KoDEx runs in a separate JVM ([KodexIsolationMode.PROCESS], the default) or inside the
     * Gradle daemon ([KodexIsolationMode.CLASS_LOADER]).
     */
    @get:Input
    abstract val mode: Property<KodexIsolationMode>

    /**
     * Whether KoDEx runs in a separate JVM ([KodexIsolationMode.PROCESS], the default) or inside the
     * Gradle daemon ([KodexIsolationMode.CLASS_LOADER]).
     */
    fun mode(mode: KodexIsolationMode): Unit = this.mode.set(mode)

    /**
     * Maximum heap size of the KoDEx JVM, like `"1g"`.
     * Defaults to `"1g"`. Ignored when [mode] is [KodexIsolationMode.CLASS_LOADER].
     */
    @get:Input
    @get:Optional
    abstract val maxHeapSize: Property<String>

    /**
     * Maximum heap size of the KoDEx JVM, like `"1g"`.
     * Defaults to `"1g"`. Ignored when [mode] is [KodexIsolationMode.CLASS_LOADER].
     */
    fun maxHeapSize(size: String?): Unit = maxHeapSize.set(size)

    /**
     * Extra JVM arguments for the KoDEx JVM.
     * Ignored when [mode] is [KodexIsolationMode.CLASS_LOADER].
     */
    @get:Input
    abstract val jvmArgs: ListProperty<String>

    /**
     * Extra JVM arguments for the KoDEx JVM.
     * Ignored when [mode] is [KodexIsolationMode.CLASS_LOADER].
     */
    fun jvmArgs(vararg args: String): Unit = jvmArgs.set(args.toList())
}
