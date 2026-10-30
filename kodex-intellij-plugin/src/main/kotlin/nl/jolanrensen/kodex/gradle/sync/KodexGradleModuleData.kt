package nl.jolanrensen.kodex.gradle.sync

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData
import nl.jolanrensen.kodex.gradle.KodexSourceSetConfiguration
import org.jetbrains.plugins.gradle.util.GradleConstants

/**
 * The KoDEx configuration of one Gradle project (module), attached to the module's
 * [com.intellij.openapi.externalSystem.model.DataNode] during Gradle sync by [KodexGradleProjectResolverExtension]
 * and consumed by [KodexGradleDataService].
 */
class KodexGradleModuleData(
    val kodexVersion: String,
    val configurations: List<KodexSourceSetConfiguration>,
) : AbstractExternalEntityData(GradleConstants.SYSTEM_ID) {

    companion object {
        @JvmField
        val KEY: Key<KodexGradleModuleData> = Key.create(
            KodexGradleModuleData::class.java,
            ProjectKeys.MODULE.processingWeight + 1,
        )
    }
}
