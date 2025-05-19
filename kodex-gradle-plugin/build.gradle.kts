import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    java
    kotlin("jvm")
    id("com.gradle.plugin-publish") version "1.1.0"
    signing
    id("com.github.johnrengelman.shadow")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "nl.jolanrensen.kodex"
version = "0.4.4-SNAPSHOT"

publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = uri("~/.m2/repository")
        }
    }
}

repositories {
    // Use Maven Central for resolving dependencies
    mavenCentral()
    maven("https://plugins.gradle.org/m2/")
}

dependencies {
    api(project(":kodex-common"))

    // Gradle plugin dependencies
    shadow(gradleApi())
    shadow(gradleKotlinDsl())
    compileOnly("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:2.0.20")
    compileOnly("org.jetbrains.kotlin.multiplatform:org.jetbrains.kotlin.multiplatform.gradle.plugin:2.0.20")

    // Dokka dependencies
    val dokkaVersion = "2.0.0"
    shadow("org.jetbrains.dokka:analysis-kotlin-symbols:$dokkaVersion")
    shadow("org.jetbrains.dokka:dokka-base:$dokkaVersion")
    shadow("org.jetbrains.dokka:dokka-core:$dokkaVersion")
    shadow("org.jetbrains.dokka:dokka-base-test-utils:$dokkaVersion")
    shadow("org.jetbrains.dokka:dokka-gradle-plugin:$dokkaVersion")

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")

    // shadowed in kodex-common for intellij plugin, but we need it here
    implementation("org.jetbrains:markdown-jvm:0.6.1")
}

tasks.shadowJar {
    dependsOn(":kodex-common:shadowJar")
    isZip64 = true
    archiveClassifier = ""

    // Avoid clashes with org.jetbrains:markdown-jvm:0.6.1 in :common
    relocate("org.intellij.markdown", "nl.jolanrensen.kodex.markdown")
}

gradlePlugin {
    website = "https://github.com/Jolanrensen/KoDEx"
    vcsUrl = "https://github.com/Jolanrensen/KoDEx"

    fun PluginDeclaration.commonConfig() {
        displayName = "/** KoDEx */: Kotlin Documentation Extensions"
        description = "/** KoDEx */: Kotlin Documentation Extensions"
        tags = listOf(
            "kodex",
            "kotlin",
            "java",
            "documentation",
            "library",
            "preprocessor",
            "plugins",
            "documentation-tool",
            "javadoc",
            "documentation-generator",
            "library-management",
            "kdoc",
            "javadocs",
            "preprocessors",
            "kdocs",
            "tags",
            "tag",
            "tag-processor",
        )
        implementationClass = "nl.jolanrensen.kodex.gradle.KodexPlugin"
    }

    // Deprecated: Will be deprecated in favor of nl.jolanrensen.kodex
    val docProcessor by plugins.creating {
        commonConfig()
        description = "Deprecated: Will be deprecated in favor of nl.jolanrensen.kodex"
        id = "nl.jolanrensen.docProcessor"
    }

    val kodex by plugins.creating {
        commonConfig()
        id = "nl.jolanrensen.kodex"
    }
}

// Add a source set and a task for a functional test suite
val functionalTest: SourceSet by sourceSets.creating
gradlePlugin.testSourceSets(functionalTest)

configurations[functionalTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())

val functionalTestTask = tasks.register<Test>("functionalTest") {
    testClassesDirs = functionalTest.output.classesDirs
    classpath = configurations[functionalTest.runtimeClasspathConfigurationName] +
        functionalTest.output
}

tasks.check {
    // Run the functional tests as part of `check`
    dependsOn(functionalTestTask)
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_11
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}
