import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.ktlint)
}

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
            sinceBuild = "262"
            untilBuild = "263.*"
        }
    }
    pluginVerification {
        cliPath.set(file("verifier-all.jar"))

        ides {
            create(IntelliJPlatformType.IntellijIdeaUltimate, libs.versions.intellijVerificationIde.get()) {
                useInstaller = false
            }
            recommended()
        }
    }
}

dependencies {
    implementation(projects.kodexCommon)

    // Tooling API model built by the KoDEx Gradle plugin, requested during Gradle sync.
    // Kept as a separate, dependency-free jar since it's shipped to the Gradle daemon,
    // see KodexGradleProjectResolverExtension.
    implementation(projects.kodexToolingModel)

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation(libs.kotestAssertionsCore)

    intellijPlatform {
        intellijIdeaUltimate(libs.versions.intellijIdea.get())
        bundledPlugins(
            "org.jetbrains.kotlin",
            "com.intellij.java",
            "org.intellij.plugins.markdown",
            // optional dependency, for the connection to the KoDEx Gradle plugin
            "com.intellij.gradle",
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
    dependsOn(":kodex-common:shadowJar")
    compilerOptions {
        jvmTarget = JvmTarget.JVM_25
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        sourceCompatibility = JavaVersion.VERSION_25
    }
}

tasks.withType<PrepareSandboxTask> {
    dependsOn(":kodex-common:shadowJar")
}
