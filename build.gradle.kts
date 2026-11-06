plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.mavenPublish) apply false
}

repositories {
    mavenCentral()
}
