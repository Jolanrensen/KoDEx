import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR_LOG_NOT_FOUND
import nl.jolanrensen.kodex.gradle.creatingRunKodexTask

plugins {
    kotlin("jvm") version "2.2.10"

    // adding the Gradle plugin
    id("nl.jolanrensen.kodex") version "0.4.5-SNAPSHOT"
}

group = "nl.jolanrensen.example"
version = "1.0"

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation("org.jetbrains.kotlinx:dataframe:0.15.0")
    testImplementation(kotlin("test"))

    implementation("androidx.compose.runtime:runtime:1.7.6")
    implementation("androidx.compose.ui:ui:1.7.6")
}

// new experimental gradle extension
kodex {
    preprocess(kotlin.sourceSets.main) {
        // optional setup
        arguments(ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false)
    }
}

// old KoDEx notation
val kotlinMainSources: FileCollection = kotlin.sourceSets.main.get().kotlin.sourceDirectories

val preprocessMainKodexOld by creatingRunKodexTask(sources = kotlinMainSources) {
    arguments(ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false)
}

// Modify all Jar tasks such that before running the Kotlin sources are set to
// the target of processKdocMain and they are returned back to normal afterwards.
tasks.withType<Jar> {
    dependsOn(preprocessMainKodexOld)
    outputs.upToDateWhen { false }

    doFirst {
        kotlin {
            sourceSets {
                main {
                    kotlin.setSrcDirs(preprocessMainKodexOld.targets)
                }
            }
        }
    }

    doLast {
        kotlin {
            sourceSets {
                main {
                    kotlin.setSrcDirs(kotlinMainSources)
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
