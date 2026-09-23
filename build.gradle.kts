import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    id("java-library")
    id("de.eldoria.plugin-yml.paper") version "0.9.0"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "io.github.offensichtlicher"
version = "1.0-SNAPSHOT"

repositories {

    mavenCentral()
    maven(url = "https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    paperLibrary("com.github.bsommerfeld.pathetic-bukkit:core:5.5.2")
}

paper {
    name = "NavigationHelper"
    author = "Offensichtlicher"
    main = "io.github.offensichtlicher.patheticnavigation.NavigationHelperPlugin"
    loader = "io.github.offensichtlicher.patheticnavigation.PluginLibrariesLoader"
    generateLibrariesJson = true
    // pathetic-bukkit 5.5.2 reads chunks from arbitrary worker threads
    // (PaperChunkDataProvider, FailingNavigationPointProvider) and is not region-safe.
    foliaSupported = false
    apiVersion = "1.26"
    load = BukkitPluginDescription.PluginLoadOrder.POSTWORLD

    permissions {
        register("navigation.command.goal") {
            description = "Allows using /goal to set or reset a navigation goal"
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }
}

tasks {
    // Paper refuses repo.maven.apache.org as a plugin library repository; route it through
    // the default Maven Central proxy instead.
    generatePaperPluginDescription {
        useDefaultCentralProxy()
    }

    runServer {
        minecraftVersion("26.2")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}
