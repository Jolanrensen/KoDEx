package nl.jolanrensen.kodex.gradle

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import kotlin.test.Test

class KodexFileContextTest {

    private val root = File("/project").absoluteFile

    private fun config(
        projectPath: String,
        sourceSetName: String,
        sourceRoots: List<String>,
        contextualSourceRoots: List<String> = emptyList(),
        processors: List<String> = listOf("a.B"),
    ) = KodexSourceSetConfiguration(
        projectPath = projectPath,
        sourceSetName = sourceSetName,
        newSourceSetName = "${sourceSetName}Kodex",
        taskName = "preprocess${sourceSetName.replaceFirstChar { it.titlecase() }}Kodex",
        sourceRoots = sourceRoots.map { File(root, it).path },
        contextualSourceRoots = contextualSourceRoots.map { File(root, it).path },
        contextualSourceSetNames = emptyList(),
        crossModuleContextualSourceSets = emptyList(),
        processors = processors,
        arguments = emptyMap(),
        pluginClasspath = emptyList(),
        processLimit = 10_000,
    )

    private val libMain = config(
        projectPath = ":lib",
        sourceSetName = "main",
        sourceRoots = listOf("lib/src/main/kotlin", "lib/src/main/java"),
        contextualSourceRoots = listOf("lib/src/test/kotlin", "core/src/main/kotlin"),
        processors = listOf("a.Include"),
    )

    private val libTest = config(
        projectPath = ":lib",
        sourceSetName = "test",
        sourceRoots = listOf("lib/src/test/kotlin"),
        processors = listOf("a.Include", "a.Comment"),
    )

    private val configurations = listOf(libMain, libTest)

    private fun file(path: String) = File(root, path)

    @Test
    fun `no configurations means standalone`() {
        resolveKodexFileContext(file("lib/src/main/kotlin/A.kt"), emptyList()) shouldBe KodexFileContext.Standalone
    }

    @Test
    fun `file in preprocessed source set is connected to that configuration`() {
        val context = resolveKodexFileContext(file("lib/src/main/kotlin/com/example/A.kt"), configurations)
            .shouldBeInstanceOf<KodexFileContext.Connected>()

        context.configuration shouldBe libMain
        context.isContextualOnly.shouldBeFalse()
        context.visibleSourceRoots shouldContainExactlyInAnyOrder listOf(
            file("lib/src/main/kotlin").path,
            file("lib/src/main/java").path,
            file("lib/src/test/kotlin").path,
            file("core/src/main/kotlin").path,
        )

        // contextualSourceSets() of :lib/main: own sources, test sources and :core/main are visible
        context.isVisible(file("lib/src/main/java/com/example/B.java")).shouldBeTrue()
        context.isVisible(file("lib/src/test/kotlin/com/example/ATest.kt")).shouldBeTrue()
        context.isVisible(file("core/src/main/kotlin/Core.kt")).shouldBeTrue()

        // ...but nothing else, just like in the Gradle build
        context.isVisible(file("other/src/main/kotlin/Other.kt")).shouldBeFalse()
        context.isVisible(file("core/src/test/kotlin/CoreTest.kt")).shouldBeFalse()
        // no prefix matching on directory names
        context.isVisible(file("lib/src/main/kotlinx/X.kt")).shouldBeFalse()
    }

    @Test
    fun `preprocessed source set wins over being contextual for another one`() {
        // lib/src/test/kotlin is preprocessed by :lib/test and contextual for :lib/main
        val context = resolveKodexFileContext(file("lib/src/test/kotlin/ATest.kt"), configurations)
            .shouldBeInstanceOf<KodexFileContext.Connected>()

        context.configuration shouldBe libTest
        context.isContextualOnly.shouldBeFalse()
        // :lib/test has no contextual source sets, so only its own roots are visible
        context.visibleSourceRoots shouldContainExactlyInAnyOrder listOf(file("lib/src/test/kotlin").path)
        context.isVisible(file("lib/src/main/kotlin/A.kt")).shouldBeFalse()
    }

    @Test
    fun `file that is only contextual uses the configuration it is context for`() {
        val context = resolveKodexFileContext(file("core/src/main/kotlin/Core.kt"), configurations)
            .shouldBeInstanceOf<KodexFileContext.Connected>()

        context.configuration shouldBe libMain
        context.isContextualOnly.shouldBeTrue()
        context.visibleSourceRoots shouldContainExactlyInAnyOrder libMain.visibleSourceRoots
    }

    @Test
    fun `file outside all source sets is standalone`() {
        resolveKodexFileContext(file("other/src/main/kotlin/Other.kt"), configurations) shouldBe
            KodexFileContext.Standalone
        resolveKodexFileContext(file("lib/build.gradle.kts"), configurations) shouldBe
            KodexFileContext.Standalone
    }

    @Test
    fun `most specific source root wins when roots overlap`() {
        val outer = config(":a", "main", sourceRoots = listOf("src"))
        val inner = config(":a", "generated", sourceRoots = listOf("src/generated"))

        resolveKodexFileContext(file("src/generated/G.kt"), listOf(outer, inner))
            .shouldBeInstanceOf<KodexFileContext.Connected>()
            .configuration shouldBe inner
        resolveKodexFileContext(file("src/main/M.kt"), listOf(outer, inner))
            .shouldBeInstanceOf<KodexFileContext.Connected>()
            .configuration shouldBe outer
    }

    @Test
    fun `arguments keep their type through encoding`() {
        val arguments = mapOf<String, Any?>(
            "bool" to false,
            "int" to 42,
            "long" to 42L,
            "double" to 1.5,
            "string" to "b:looks like a boolean",
            "null" to null,
        )
        val roundTripped = arguments
            .mapValues { (_, v) -> KodexSourceSetConfiguration.encodeArgument(v) }
            .mapValues { (_, v) -> KodexSourceSetConfiguration.decodeArgument(v) }

        roundTripped shouldBe arguments
    }

    @Test
    fun `processors key is shared between identical processor configurations`() {
        val main = config(":a", "main", sourceRoots = listOf("src/main/kotlin"))
        val test = config(":a", "test", sourceRoots = listOf("src/test/kotlin"))
        val other = config(":a", "other", sourceRoots = listOf("src/other/kotlin"), processors = listOf("a.C"))

        main.processorsKey shouldBe test.processorsKey
        (main.processorsKey == other.processorsKey).shouldBeFalse()
    }
}
