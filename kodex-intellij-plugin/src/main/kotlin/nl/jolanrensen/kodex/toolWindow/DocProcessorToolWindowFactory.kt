package nl.jolanrensen.kodex.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import nl.jolanrensen.kodex.BooleanSetting
import nl.jolanrensen.kodex.EnumSetting
import nl.jolanrensen.kodex.MessageBundle
import nl.jolanrensen.kodex.allSettings
import nl.jolanrensen.kodex.getLoadedProcessors
import nl.jolanrensen.kodex.services.DocProcessorService
import javax.swing.JComponent

class DocProcessorToolWindowFactory : ToolWindowFactory {

    private val contentFactory = ContentFactory.getInstance()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(
            @Suppress("ktlint:standard:comment-wrapping")
            contentFactory.createContent(
                /* component = */ DocProcessorToolWindow(toolWindow).getContent(),
                /* displayName = */ null,
                /* isLockable = */ false,
            ),
        )
    }

    override fun shouldBeAvailable(project: Project) = true

    class DocProcessorToolWindow(val toolWindow: ToolWindow) {

        private lateinit var panel: DialogPanel

        fun getContent(): JComponent {
            panel = panel {
                indent {
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

                    group(MessageBundle.message("loadedPreprocessors")) {
                        val loadedPreprocessors = DocProcessorService::class.java.classLoader.getLoadedProcessors()

                        if (loadedPreprocessors.isEmpty()) row(MessageBundle.message("noPreprocessorsLoaded")) {}
                        for ((i, preProcessor) in loadedPreprocessors.withIndex()) {
                            row("${i + 1}. ${preProcessor.name}") {}
                        }

                        row { text(MessageBundle.message("loadedPreprocessorsMessage")) }
                    }
                }
            }
            return panel
        }
    }
}
