plugins {
    kotlin("jvm")
    application
}

group = "nl.jolanrensen.example"
version = "1.0.0"
application {
    mainClass.set("nl.jolanrensen.example.ApplicationKt")
}

kotlin {
}

dependencies {
    implementation(projects.common)
}
