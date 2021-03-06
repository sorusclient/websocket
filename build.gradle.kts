/*
 * Copyright (c) 2022. Danterus
 * Copyright (c) 2022. Sorus Contributors
 *
 * SPDX-License-Identifier: MPL-2.0
 */

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.20")
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