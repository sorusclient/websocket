plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.10"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
    implementation("io.ktor:ktor-server-netty:1.6.8")
    implementation("io.ktor:ktor-websockets:1.6.8")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    implementation("org.json:json:20211205")
    implementation("io.ktor:ktor-client-cio:1.6.8")
}