package nl.jolanrensen.kodex

import org.gradle.testkit.runner.GradleRunner
import org.junit.Ignore
import org.junit.Test
import java.io.File

class DataFrameTest : DocProcessorFunctionalTest("df") {

    private val dfProjectDirectory = File("src/functionalTest/resources/dataframe")
    private val dfOutputDirectory = File(dfProjectDirectory, "core/build/generated-sources")

    init {
        require(dfProjectDirectory.exists()) {
            "The DataFrame directory does not exist. Please git clone the repository in the resources folder."
        }
        updateVersionInFiles()
    }

    private fun updateVersionInFiles() {
        val tomlFile = dfProjectDirectory.resolve("gradle/libs.versions.toml")
        var txt = tomlFile.readText()
        txt = txt
            .replace("docProcessor = \"[^\"]+\"".toRegex(), "docProcessor = \"$version\"")
            .replace("kodex = \"[^\"]+\"".toRegex(), "kodex = \"$version\"")
        tomlFile.write(txt)
    }

    @Ignore
    @Test
    fun `build DataFrame`() {
        GradleRunner.create()
            .forwardOutput()
            .withArguments("clean", "processKDocsMain")
            .withProjectDir(dfProjectDirectory)
            .withDebug(true)
            .build()
    }
}
