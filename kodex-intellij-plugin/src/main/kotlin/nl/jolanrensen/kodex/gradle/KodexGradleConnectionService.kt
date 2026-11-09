package nl.jolanrensen.kodex.gradle

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.messages.Topic
import nl.jolanrensen.kodex.kodexGradleConnectionIsEnabled
import java.io.File

/**
 * Holds the KoDEx configurations imported from the Gradle build(s) linked to this project and decides,
 * per file, whether KoDEx runs [KodexFileContext.Standalone] or [KodexFileContext.Connected].
 *
 * The configurations are filled by `KodexGradleDataService` after each Gradle sync
 * (when the Gradle plugin of the IDE is available and the build applies the KoDEx Gradle plugin)
 * and persisted in the project's cache so they survive a restart without re-syncing.
 *
 * This class deliberately does not depend on any Gradle classes of the IDE, so the rest of the plugin can use it
 * even when the IDE's Gradle plugin is not installed (then the service just stays empty: standalone mode).
 */
@Service(Service.Level.PROJECT)
@State(name = "KodexGradleConnection", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class KodexGradleConnectionService(private val project: Project) :
    PersistentStateComponent<KodexGradleConnectionService.State> {

    companion object {
        fun getInstance(project: Project): KodexGradleConnectionService = project.service()

        @JvmField
        val TOPIC: Topic<KodexGradleConnectionListener> = Topic.create(
            "KoDEx Gradle connection changed",
            KodexGradleConnectionListener::class.java,
        )

        private val logger = logger<KodexGradleConnectionService>()
    }

    /** Called whenever the imported configurations change, e.g. after a Gradle sync. */
    fun interface KodexGradleConnectionListener {
        fun connectionChanged(service: KodexGradleConnectionService)
    }

    /** Persisted form of the configurations, grouped per linked Gradle build. */
    class State {
        var builds: MutableList<BuildState> = mutableListOf()
    }

    class BuildState {
        /**
         * The path of the root of the linked Gradle build,
         * [com.intellij.openapi.externalSystem.model.project.ProjectData.getLinkedExternalProjectPath].
         */
        var externalProjectPath: String = ""
        var kodexVersion: String = ""
        var configurations: MutableList<SourceSetState> = mutableListOf()
    }

    class SourceSetState {
        var projectPath: String = ""
        var sourceSetName: String = ""
        var newSourceSetName: String = ""
        var taskName: String = ""
        var sourceRoots: MutableList<String> = mutableListOf()
        var contextualSourceRoots: MutableList<String> = mutableListOf()
        var contextualSourceSetNames: MutableList<String> = mutableListOf()
        var crossModuleContextualSourceSets: MutableList<String> = mutableListOf()
        var processors: MutableList<String> = mutableListOf()
        var arguments: MutableMap<String, String> = mutableMapOf()
        var pluginClasspath: MutableList<String> = mutableListOf()
        var processLimit: Int = 10_000
    }

    @Volatile
    private var state = State()

    @Volatile
    private var cachedConfigurations: List<KodexSourceSetConfiguration> = emptyList()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        rebuildCache()
    }

    private fun rebuildCache() {
        cachedConfigurations = state.builds.flatMap { build -> build.configurations.map { it.toConfiguration() } }
    }

    /** All configurations imported from Gradle, regardless of [kodexGradleConnectionIsEnabled]. */
    val importedConfigurations: List<KodexSourceSetConfiguration> get() = cachedConfigurations

    /** The versions of the KoDEx Gradle plugin of the linked builds that apply KoDEx. */
    val importedKodexVersions: List<String>
        get() = state.builds.filter { it.configurations.isNotEmpty() }.map { it.kodexVersion }.distinct()

    /**
     * The configurations that are in effect: the imported ones, unless the connection is disabled in the settings.
     */
    val configurations: List<KodexSourceSetConfiguration>
        get() = if (kodexGradleConnectionIsEnabled) cachedConfigurations else emptyList()

    /** Whether at least one `preprocess` configuration is imported and the connection is enabled. */
    val isConnected: Boolean get() = configurations.isNotEmpty()

    /**
     * Replaces the configurations of the Gradle build at [externalProjectPath] with [configurations].
     * Called after a Gradle sync; an empty list means the build does not (or no longer) use(s) KoDEx.
     */
    fun updateConfigurations(
        externalProjectPath: String,
        kodexVersion: String,
        configurations: List<KodexSourceSetConfiguration>,
    ) {
        logger.info(
            "KoDEx: imported ${configurations.size} preprocess configuration(s) from Gradle build at " +
                "$externalProjectPath: ${configurations.map { it.displayName }}",
        )

        state.builds.removeAll { it.externalProjectPath == externalProjectPath }
        if (configurations.isNotEmpty()) {
            state.builds += BuildState().also { build ->
                build.externalProjectPath = externalProjectPath
                build.kodexVersion = kodexVersion
                build.configurations = configurations.map { it.toState() }.toMutableList()
            }
        }
        rebuildCache()

        notifyChanged()
    }

    /** Lets listeners know the effective configuration changed (also used when the setting is toggled). */
    fun notifyChanged() {
        if (project.isDisposed) return
        project.messageBus.syncPublisher(TOPIC).connectionChanged(this)
    }

    fun contextFor(file: File): KodexFileContext = resolveKodexFileContext(file, configurations)

    fun contextFor(file: VirtualFile?): KodexFileContext {
        if (file == null || !file.isInLocalFileSystem) return KodexFileContext.Standalone
        return contextFor(File(file.path))
    }

    fun contextFor(psiFile: PsiFile?): KodexFileContext = contextFor(psiFile?.originalFile?.virtualFile)

    fun contextFor(element: PsiElement): KodexFileContext = contextFor(psiFile = element.containingFile)

    private fun SourceSetState.toConfiguration(): KodexSourceSetConfiguration =
        KodexSourceSetConfiguration(
            projectPath = projectPath,
            sourceSetName = sourceSetName,
            newSourceSetName = newSourceSetName,
            taskName = taskName,
            sourceRoots = sourceRoots.toList(),
            contextualSourceRoots = contextualSourceRoots.toList(),
            contextualSourceSetNames = contextualSourceSetNames.toList(),
            crossModuleContextualSourceSets = crossModuleContextualSourceSets.toList(),
            processors = processors.toList(),
            arguments = arguments.toMap(),
            pluginClasspath = pluginClasspath.toList(),
            processLimit = processLimit,
        )

    private fun KodexSourceSetConfiguration.toState(): SourceSetState =
        SourceSetState().also {
            it.projectPath = projectPath
            it.sourceSetName = sourceSetName
            it.newSourceSetName = newSourceSetName
            it.taskName = taskName
            it.sourceRoots = sourceRoots.toMutableList()
            it.contextualSourceRoots = contextualSourceRoots.toMutableList()
            it.contextualSourceSetNames = contextualSourceSetNames.toMutableList()
            it.crossModuleContextualSourceSets = crossModuleContextualSourceSets.toMutableList()
            it.processors = processors.toMutableList()
            it.arguments = arguments.toMutableMap()
            it.pluginClasspath = pluginClasspath.toMutableList()
            it.processLimit = processLimit
        }
}
