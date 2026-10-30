package nl.jolanrensen.kodex.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import nl.jolanrensen.kodex.BooleanSetting
import nl.jolanrensen.kodex.EnumSetting
import nl.jolanrensen.kodex.KodexGradleConnectionIsEnabled
import nl.jolanrensen.kodex.MessageBundle
import nl.jolanrensen.kodex.allSettings
import nl.jolanrensen.kodex.gradle.KodexFileContext
import nl.jolanrensen.kodex.gradle.KodexGradleConnectionService
import nl.jolanrensen.kodex.gradle.KodexSourceSetConfiguration
import nl.jolanrensen.kodex.services.DocProcessorService
import nl.jolanrensen.kodex.standaloneProcessors
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class DocProcessorToolWindowFactory : ToolWindowFactory {

    private val contentFactory = ContentFactory.getInstance()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = contentFactory.createContent(
            /* component = */ DocProcessorToolWindow(project, toolWindow).getContent(),
            /* displayName = */ null,
            /* isLockable = */ false,
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class DocProcessorToolWindow(val project: Project, val toolWindow: ToolWindow) {

        private lateinit var panel: DialogPanel

        /** Wraps [panel], so it can be rebuilt when the Gradle connection changes. */
        private val container = JPanel(BorderLayout())

        private val gradleConnection get() = KodexGradleConnectionService.getInstance(project)

        fun getContent(): JComponent {
            rebuild()

            val disposable: Disposable = toolWindow.disposable
            project.messageBus.connect(disposable).subscribe(
                KodexGradleConnectionService.TOPIC,
                KodexGradleConnectionService.KodexGradleConnectionListener {
                    ApplicationManager.getApplication().invokeLater({ rebuild() }, project.disposed)
                },
            )

            // re-render when the connection setting is toggled, the processors shown depend on it
            KodexGradleConnectionIsEnabled.afterChange(disposable) {
                gradleConnection.notifyChanged()
            }

            return JBScrollPane(container)
        }

        private fun rebuild() {
            panel = panel {
                indent {
                    settingsGroup()
                    connectionGroup()
                }
            }
            container.removeAll()
            container.add(panel, BorderLayout.NORTH)
            container.revalidate()
            container.repaint()
        }

        private fun Panel.settingsGroup() {
            group(MessageBundle.message("settings")) {
                for (setting in allSettings) {
                    row {
                        when (setting) {
                            is BooleanSetting ->
                                checkBox(MessageBundle.message(setting.messageBundleName))
                                    .bindSelected(setting)
                                    .enabledIf(setting.isEnabled)
                                    .onChanged {
                                        panel.apply()
                                    }

                            is EnumSetting<*> -> {
                                label(MessageBundle.message(setting.messageBundleName))
                                comboBox(setting.values)
                                    .bindItem({ setting.value }) {
                                        it?.let { setting.setValueAsAny(it) }
                                    }
                                    .enabledIf(setting.isEnabled)
                                    .onChanged {
                                        panel.apply()
                                    }
                            }
                        }
                    }
                }
                row { text(MessageBundle.message("changeSettings")) }
            }
        }

        /**
         * Shows whether KoDEx runs standalone or connected to the Gradle build, and the processors
         * in use: the defaults when standalone, or the configuration per `preprocess`ed source set when connected.
         */
        private fun Panel.connectionGroup() {
            val connection = gradleConnection
            val imported = connection.importedConfigurations

            group(MessageBundle.message("gradleConnection")) {
                when {
                    connection.isConnected -> {
                        row {
                            text(
                                MessageBundle.message(
                                    "gradleConnectionConnected",
                                    imported.size,
                                    connection.importedKodexVersions.joinToString(", "),
                                ),
                            )
                        }
                        row { text(MessageBundle.message("gradleConnectionConnectedMessage")) }
                    }

                    imported.isNotEmpty() -> {
                        // imported, but disabled in the settings
                        row { text(MessageBundle.message("gradleConnectionDisabled", imported.size)) }
                    }

                    else -> {
                        row { text(MessageBundle.message("gradleConnectionStandalone")) }
                        row { text(MessageBundle.message("gradleConnectionStandaloneMessage")) }
                    }
                }
            }

            if (connection.isConnected) {
                for (configuration in connection.configurations) {
                    sourceSetGroup(configuration)
                }
            } else {
                group(MessageBundle.message("loadedPreprocessors")) {
                    processorRows(standaloneProcessors, missing = emptyList())
                    row { text(MessageBundle.message("loadedPreprocessorsMessage")) }
                }
            }
        }

        private fun Panel.sourceSetGroup(configuration: KodexSourceSetConfiguration) {
            val missing = try {
                DocProcessorService.getInstance(project).processorsFor(configuration.toConnectedContext())
                    .map { it::class.qualifiedName }
                    .let { loaded -> configuration.processors.filter { it !in loaded } }
            } catch (_: Throwable) {
                configuration.processors
            }

            collapsibleGroup(MessageBundle.message("sourceSetGroup", configuration.displayName)) {
                row(MessageBundle.message("sourceSetTask")) { label(configuration.taskName) }
                row(MessageBundle.message("sourceSetSourceRoots")) {
                    label(configuration.sourceRoots.joinToString("<br>").asHtml())
                }

                val contextual = configuration.contextualSourceSetNames + configuration.crossModuleContextualSourceSets
                row(MessageBundle.message("sourceSetContextualSourceSets")) {
                    label(contextual.ifEmpty { listOf(MessageBundle.message("none")) }.joinToString(", "))
                }
                if (configuration.contextualSourceRoots.isNotEmpty()) {
                    row(MessageBundle.message("sourceSetContextualSourceRoots")) {
                        label(configuration.contextualSourceRoots.joinToString("<br>").asHtml())
                    }
                }
                if (configuration.arguments.isNotEmpty()) {
                    row(MessageBundle.message("sourceSetArguments")) {
                        label(
                            configuration.decodedArguments.entries
                                .joinToString("<br>") { (k, v) -> "$k = $v" }
                                .asHtml(),
                        )
                    }
                }
                if (configuration.pluginClasspath.isNotEmpty()) {
                    row(MessageBundle.message("sourceSetPlugins")) {
                        label(configuration.pluginClasspath.joinToString("<br>").asHtml())
                    }
                }
                row(MessageBundle.message("loadedPreprocessors")) {}
                processorRows(configuration.processors, missing)
            }.apply { expanded = true }
        }

        private fun Panel.processorRows(processors: List<String>, missing: List<String>) {
            if (processors.isEmpty()) row(MessageBundle.message("noPreprocessorsLoaded")) {}
            for ((i, processor) in processors.withIndex()) {
                val name = processor.substringAfterLast('.')
                if (processor in missing) {
                    row("${i + 1}. $name") { text(MessageBundle.message("processorNotLoaded")) }
                } else {
                    row("${i + 1}. $name") {}
                }
            }
        }

        private fun String.asHtml() = "<html>$this</html>"

        private fun KodexSourceSetConfiguration.toConnectedContext() =
            KodexFileContext.Connected(
                configuration = this,
                visibleSourceRoots = visibleSourceRoots,
            )
    }
}
