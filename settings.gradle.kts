pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
    plugins {
        // Uploads to Modrinth: `./gradlew publishModrinth`.
        id("com.modrinth.minotaur") version "2.10.0"
    }
}

plugins {
    // Multi-version preprocessor: one shared `src/`, one Gradle subproject per Minecraft version.
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Applies `fabric-loom-remap` to obfuscated versions (<26.1) and `fabric-loom` to unobfuscated ones.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    // Lets Gradle download the JDKs each version needs (21 for 1.21.x, 25 for 26.x).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // One node per released jar, named after the Minecraft version it compiles against.
        // The releases each jar supports are listed under `mod.mc_compat` in stonecutter.properties.toml.
        versions(
            "1.21.1", "1.21.3", "1.21.4", "1.21.5", "1.21.8", "1.21.10", "1.21.11",
            "26.1.2", "26.2", "26.3",
        )
        // The version `src/` must be switched to before committing.
        vcsVersion = "26.3"
    }
}

rootProject.name = "enchantcalc"
