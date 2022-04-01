plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
    implementation("io.ktor:ktor-server-netty:1.6.8")
    implementation("io.ktor:ktor-websockets:1.6.8")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("org.json:json:20220320")
    implementation("io.ktor:ktor-client-cio:1.6.8")
    implementation("commons-io:commons-io:2.11.0")
}

tasks.withType(org.gradle.jvm.tasks.Jar::class.java) {
    manifest {
        attributes(
            "Main-Class" to "com.github.sorusclient.websocket.MainKt"
        )
    }
}