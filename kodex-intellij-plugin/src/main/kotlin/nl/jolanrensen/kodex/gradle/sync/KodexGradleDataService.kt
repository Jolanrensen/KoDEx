package nl.jolanrensen.kodex.gradle.sync

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import nl.jolanrensen.kodex.gradle.KodexGradleConnectionService

/**
 * Imports the [KodexGradleModuleData] collected during Gradle sync (by [KodexGradleProjectResolverExtension])
 * into the [KodexGradleConnectionService] of the project, switching the plugin to "connected" mode for
 * the files of the `preprocess`ed source sets.
 *
 * Registered in `kodex-gradle.xml` (optional dependency on the Gradle plugin of the IDE).
 */
class KodexGradleDataService : AbstractProjectDataService<KodexGradleModuleData, Void>() {

    override fun getTargetDataKey(): Key<KodexGradleModuleData> = KodexGradleModuleData.KEY

    override fun importData(
        toImport: Collection<DataNode<KodexGradleModuleData>>,
        projectData: ProjectData?,
        project: Project,
        modelsProvider: IdeModifiableModelsProvider,
    ) {
        // without project data we cannot tell which linked Gradle build was synced
        val externalProjectPath = projectData?.linkedExternalProjectPath ?: return

        val data = toImport.map { it.data }
        KodexGradleConnectionService.getInstance(project).updateConfigurations(
            externalProjectPath = externalProjectPath,
            kodexVersion = data.map { it.kodexVersion }.distinct().joinToString(", "),
            configurations = data.flatMap { it.configurations },
        )
    }
}
