import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
    id("com.gradleup.shadow")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "nl.jolanrensen.kodex"
version = "0.5.5-SNAPSHOT"

val kotlinVersion = "2.2.21"

repositories {
    mavenCentral()
    mavenLocal()
}

configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion",
            "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion",
        )
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.apache.commons:commons-text:1.10.0")

    shadow("org.jetbrains:markdown-jvm:0.6.1")
    api("org.jgrapht:jgrapht-core:1.5.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // logging
    api("io.github.oshai:kotlin-logging:7.0.13")

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
}

tasks.shadowJar {
    isZip64 = true
    archiveClassifier = ""

    // Avoid clashes with org.jetbrains:markdown-jvm:0.6.1 in :common
    relocate("org.intellij.markdown", "nl.jolanrensen.kodex.markdown")
}

// Make the shadow jar the module's single published artifact.
//
// The shadow jar uses `archiveClassifier = ""`, so it writes to the same path as
// the plain `jar` task (kodex-common-<version>.jar). Having two tasks produce the
// same file caused a read/write race: while `:kodex-intellij-plugin:compileKotlin`
// ran its classpath-snapshot transform on the jar, `shadowJar` was rewriting it,
// yielding "zip END header not found". Disabling the plain jar and exposing the
// shadow jar as the outgoing artifact leaves a single producer, so consumers depend
// on `shadowJar` directly (correct ordering) and get the relocated markdown classes.
tasks.jar {
    enabled = false
}
configurations {
    apiElements.configure {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
    }
    runtimeElements.configure {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.shadowJar)
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_1_8
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

// TODO users should be able to just depend on this module to build extensions
