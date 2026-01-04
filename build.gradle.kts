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
        authors += "Ovis Development"
        website = "https://github.com/smartcmd/AllayPlots"
        dependency(name = "EconomyAPI")
        dependency(name = "PlaceholderAPI")
    }
}

dependencies {
    compileOnly(group = "org.projectlombok", name = "lombok", version = "1.18.34")
    compileOnly(group = "org.allaymc", name = "economy-api", version = "0.2.2")
    compileOnly(group = "org.allaymc", name = "papi", version = "0.2.0")
    annotationProcessor(group = "org.projectlombok", name = "lombok", version = "1.18.34")
}
