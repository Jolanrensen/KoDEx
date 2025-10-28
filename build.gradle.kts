plugins {
    kotlin("jvm") version "2.2.21" apply false
    id("com.gradleup.shadow") version "9.1.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("com.vanniktech.maven.publish") version "0.34.0" apply false
}

repositories {
    mavenCentral()
}
