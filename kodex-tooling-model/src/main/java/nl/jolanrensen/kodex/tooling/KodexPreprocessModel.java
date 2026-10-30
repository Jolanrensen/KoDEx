package nl.jolanrensen.kodex.tooling;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * The configuration of a single {@code kodex { preprocess(sourceSet) { ... } }} call, i.e.,
 * of a single {@code RunKodexTask}, as seen by the IDE.
 *
 * @see KodexGradleModel#getPreprocessConfigurations()
 */
public interface KodexPreprocessModel extends Serializable {

    /** The name of the Kotlin source set that is preprocessed, e.g. {@code "main"}. */
    String getSourceSetName();

    /** The name of the source set KoDEx generates, e.g. {@code "mainKodex"}. */
    String getNewSourceSetName();

    /** The name of the Gradle task running KoDEx for this source set, e.g. {@code "preprocessMainKodex"}. */
    String getTaskName();

    /** The source root directories of {@link #getSourceSetName()}. */
    List<File> getSourceRoots();

    /**
     * All source root directories whose declarations may be referenced from {@link #getSourceRoots()}
     * but are not preprocessed themselves. Contains the roots of the contextual source sets of the same
     * project as well as those of {@link #getCrossModuleContextualSourceSets()} (already resolved to directories).
     */
    List<File> getContextualSourceRoots();

    /** Names of the contextual source sets living in the same Gradle project, e.g. {@code ["test"]}. */
    List<String> getContextualSourceSetNames();

    /**
     * Contextual source sets living in other Gradle projects, as {@code "<projectPath>/<sourceSetName>"},
     * e.g. {@code [":core/main"]}. Their directories are part of {@link #getContextualSourceRoots()}.
     */
    List<String> getCrossModuleContextualSourceSets();

    /** The fully qualified class names of the processors to run, in order. */
    List<String> getProcessors();

    /**
     * The arguments passed to the processors. Values are limited to {@link String}, {@link Boolean}, and
     * {@link Number}; anything else is converted with {@code toString()}.
     */
    Map<String, Object> getArguments();

    /**
     * The jars of the KoDEx plugins (libraries containing custom processors) declared with
     * {@code dependencies { plugin(...) }}, including their transitive dependencies. Does not contain the
     * Dokka/Kotlin analysis dependencies KoDEx itself needs on Gradle's side.
     */
    List<File> getPluginClasspath();

    /** The limit for while-true loops in processors. */
    int getProcessLimit();
}
