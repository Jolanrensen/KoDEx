package nl.jolanrensen.kodex.tooling;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * Gradle Tooling API model describing how KoDEx is configured in a single Gradle project
 * (through the {@code kodex { preprocess(...) }} extension).
 * <p>
 * It is built on the Gradle side by the KoDEx Gradle plugin
 * ({@code nl.jolanrensen.kodex.gradle.KodexToolingModelBuilder}) and requested by the KoDEx IntelliJ plugin
 * during Gradle sync, so the IDE can reuse the exact configuration of the build instead of running
 * "standalone" with its built-in defaults.
 * <p>
 * The Tooling API adapts the Gradle-side implementation to this interface by matching getter names,
 * so the implementation does not have to share this exact class. Keep this interface
 * dependency-free and only use JDK types.
 */
public interface KodexGradleModel extends Serializable {

    /** The version of the KoDEx Gradle plugin that built this model. */
    String getKodexVersion();

    /** The Gradle path of the project, e.g. {@code ":"} or {@code ":lib"}. */
    String getProjectPath();

    /** The project directory of the Gradle project. */
    File getProjectDir();

    /**
     * One entry for every {@code kodex { preprocess(sourceSet) { ... } }} call in the project,
     * i.e., one per KoDEx task.
     */
    List<KodexPreprocessModel> getPreprocessConfigurations();
}
