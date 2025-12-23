import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR_LOG_NOT_FOUND

plugins {
    kotlin("jvm") // version "2.2.21"

    // adding the Gradle plugin
    id("nl.jolanrensen.kodex") version "0.5.1-SNAPSHOT"
}

group = "nl.jolanrensen.example"
version = "1.0"

repositories {
    mavenCentral()
    mavenLocal()
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
// creates tasks: kodexMainKodexSourcesJar, kodexMainKodexJar
// produces NAME-kodex.jar and NAME-kodex-sources.jar besides the normal NAME.jar and NAME-sources.jar
kodex {
    preprocess(kotlin.sourceSets.main) {
        // optional setup
        arguments(ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false)

        // Can be enabled if `PluginExtensionTest` has been run to test if extensions work
//        processors = listOf(
//            COMMENT_DOC_PROCESSOR,
//            INCLUDE_DOC_PROCESSOR,
//            INCLUDE_FILE_DOC_PROCESSOR,
//            ARG_DOC_PROCESSOR,
//            SAMPLE_DOC_PROCESSOR,
//            EXPORT_AS_HTML_DOC_PROCESSOR,
//            REMOVE_ESCAPE_CHARS_PROCESSOR,
//            "nl.jolanrensen.extension.Extension",
//        )
//
//        dependencies {
//            plugin("nl.jolanrensen:PluginExtensionTest:0.5.1-SNAPSHOT")
//        }
    }
}

// old KoDEx notation
// val kotlinMainSources: FileCollection = kotlin.sourceSets.main.get().kotlin.sourceDirectories
//
// val preprocessMainKodexOld by creatingRunKodexTask(sources = kotlinMainSources) {
//    arguments(ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false)
// }
//
// kotlin.sourceSets["main"].kotlin.setSrcDirs(preprocessMainKodexOld.targets)
//
// Modify all Jar tasks such that before running the Kotlin sources are set to
// the target of processKdocMain and they are returned back to normal afterwards.
// tasks.withType<Jar> {
//    dependsOn(preprocessMainKodexOld)
//    outputs.upToDateWhen { false }
//
//    doFirst {
//        kotlin {
//            sourceSets {
//                main {
//                    kotlin.setSrcDirs(preprocessMainKodexOld.targets)
//                }
//            }
//        }
//    }
//
//    doLast {
//        kotlin {
//            sourceSets {
//                main {
//                    kotlin.setSrcDirs(kotlinMainSources)
//                }
//            }
//        }
//    }
// }

tasks.test {
    useJUnitPlatform()
}
