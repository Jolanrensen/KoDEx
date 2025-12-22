import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.10.5"
    id("org.jlleitschuh.gradle.ktlint")
}

group = "nl.jolanrensen.kodex"
version = "0.5.1-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        releases()
        defaultRepositories()
    }
    mavenLocal()
    maven("https://www.jetbrains.com/intellij-repository/snapshots") {
        mavenContent { snapshotsOnly() }
    }
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide")
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://www.myget.org/F/rd-snapshots/maven/")
}

intellijPlatform {
    pluginConfiguration {
        name = "/** KoDEx */: Kotlin Documentation Extensions"
        ideaVersion {
            sinceBuild = "252"
            untilBuild = "253.*"
        }
    }
    pluginVerification {
        cliPath.set(file("verifier-all.jar"))

        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.3") {
                useInstaller = false
            }
            create(IntelliJPlatformType.IntellijIdeaUltimate, "2025.3") {
                useInstaller = false
            }
            recommended()
        }
    }
}

dependencies {
    implementation(project(":kodex-common"))

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")

    intellijPlatform {
        intellijIdeaCommunity("252.27397.28")
//        intellijIdeaCommunity("251.26094.37")
//        intellijIdeaUltimate("253.28294.325")
        bundledPlugins(
            "org.jetbrains.kotlin",
            "com.intellij.java",
            "org.intellij.plugins.markdown",
        )
        zipSigner()
        testFramework(TestFrameworkType.Platform)
    }
}

tasks {
    printProductsReleases {
        channels = listOf(ProductRelease.Channel.EAP)
        types = listOf(IntelliJPlatformType.IntellijIdeaCommunity)
        untilBuild = provider { null }

        doLast {
            val latestEap = productsReleases.get().max()
        }
    }
}

tasks.named<RunIdeTask>("runIde") {
    jvmArgumentProviders += CommandLineArgumentProvider {
        listOf("-Didea.kotlin.plugin.use.k2=true")
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        sourceCompatibility = JavaVersion.VERSION_21
    }
}

tasks.withType<PrepareSandboxTask> {
    dependsOn(":kodex-common:shadowJar")
}
