import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.shadow)
    alias(libs.plugins.ktlint)
}

val kotlinVersion = libs.versions.kotlin.get()

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
    implementation(libs.commonsText)

    shadow(libs.markdownJvm)
    api(libs.jgraphtCore)

    implementation(libs.coroutinesCore)

    // logging
    api(libs.kotlinLogging)

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation(libs.kotestAssertionsCore)
    testRuntimeOnly(libs.slf4jSimple)
}

tasks.shadowJar {
    isZip64 = true
    archiveClassifier = ""

    // Avoid clashes with org.jetbrains:markdown-jvm in :common
    relocate("org.intellij.markdown", "nl.jolanrensen.kodex.markdown")

    // so we overwrite the output jar
    mustRunAfter(tasks.jar)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()

    // jgrapht requires Java 11+ at runtime
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(17)
    }
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
