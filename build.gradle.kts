plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    application
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.dmytrozinkevych"
version = "0.5"

repositories {
    mavenCentral()
}

val ktorVersion = "3.5.2"
val logbackVersion = "1.6.3"
val kotlinLoggingVersion = "8.0.4"
val owaspEncoderVersion = "1.4.0"

dependencies {
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))

    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-cio")
    implementation("io.ktor:ktor-server-body-limit")

    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-auth")
    implementation("io.ktor:ktor-client-logging")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingVersion")

    implementation("org.owasp.encoder:encoder:$owaspEncoderVersion")

    testImplementation(kotlin("test"))

    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("io.ktor:ktor-client-mock")
    testImplementation("io.ktor:ktor-client-content-negotiation")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("io.github.dmytrozinkevych.homerstats.MainKt")
}