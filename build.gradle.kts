plugins {
    kotlin("jvm") version "2.4.0"
}

group = "io.github.january"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(24)
}

dependencies {
    // Mobile automation
    implementation("io.appium:java-client:10.1.1")
    implementation("org.seleniumhq.selenium:selenium-java:4.43.0")

    // devices.yml
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.0")

    // Для JUnit extensions внутри src/main
    implementation("org.junit.jupiter:junit-jupiter-api:5.13.4")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.13.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}