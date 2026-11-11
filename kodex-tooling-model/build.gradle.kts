plugins {
    java
    alias(libs.plugins.mavenPublish)
}

repositories {
    mavenCentral()
}

// This module is written in plain Java on purpose and has no dependencies:
// the IntelliJ plugin ships this jar to the Gradle daemon (as a "tooling extension") so the Gradle
// Tooling API can adapt the model built by the KoDEx Gradle plugin to these interfaces.
// It must therefore be loadable in a bare classloader, without the Kotlin stdlib.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
