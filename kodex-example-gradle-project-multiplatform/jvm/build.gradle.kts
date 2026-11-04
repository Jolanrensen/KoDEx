plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

group = "nl.jolanrensen.example"
version = libs.versions.exampleMultiplatformProject.get()
application {
    mainClass.set("nl.jolanrensen.example.ApplicationKt")
}

kotlin {
}

dependencies {
    implementation(projects.common)
}
