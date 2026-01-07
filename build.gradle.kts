plugins {
    id("java-library")
    id("org.allaymc.gradle.plugin") version "0.2.1"
}

group = "me.daoge.allayplots"
description = "Plot plugin for AllayMC"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

allay {
    api = "0.20.0-SNAPSHOT"

    plugin {
        entrance = ".AllayPlotsPlugin"
        authors += "daoge_cmd"
        website = "https://github.com/smartcmd/AllayPlots"
        dependency(name = "EconomyAPI")
        dependency(name = "PlaceholderAPI")
    }
}

repositories {
    maven("https://storehouse.okaeri.eu/repository/maven-public/")
}

dependencies {
    compileOnly(group = "org.projectlombok", name = "lombok", version = "1.18.34")
    compileOnly(group = "org.allaymc", name = "economy-api", version = "0.2.2")
    compileOnly(group = "org.allaymc", name = "papi", version = "0.2.0")
    implementation(group = "eu.okaeri", name = "okaeri-configs-yaml-snakeyaml", version = "5.0.13")
    implementation(group = "org.xerial", name = "sqlite-jdbc", version = "3.47.1.0")
    implementation(group = "com.h2database", name = "h2", version = "2.4.240")
    annotationProcessor(group = "org.projectlombok", name = "lombok", version = "1.18.34")

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.10.0")
    testImplementation(group = "org.assertj", name = "assertj-core", version = "3.24.2")
    testImplementation(group = "org.slf4j", name = "slf4j-simple", version = "2.0.9")
    testRuntimeOnly(group = "org.junit.platform", name = "junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
