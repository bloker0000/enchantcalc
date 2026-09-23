// Runs once per Stonecutter node (versions/<minecraft version>). `sc.current` is that node.
plugins {
    // Applies the right Loom flavour for the node's Minecraft version.
    id("dev.kikugie.loom-back-compat")
    id("com.modrinth.minotaur")
}

val minecraft = sc.current.version
// Read project properties up here: inside task blocks `property()` resolves against the task.
val modId = property("mod.id") as String
val modName = property("mod.name") as String
val modVersion = property("mod.version") as String
val minecraftCompat = property("mod.mc_compat") as String

version = "$modVersion+$minecraft"
group = property("mod.group") as String
base.archivesName = modId

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft")
    // Mojang's official names on obfuscated versions; a no-op from 26.1 on.
    loomx.applyMojangMappings()

    // `mod*` configurations are aliased to plain ones on unobfuscated Loom.
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    // One `run/` folder for every version. Only the normal client/server: the game test run clears its folder.
    runConfigs.matching { it.name == "client" || it.name == "server" }.configureEach {
        runDirectory = rootProject.file("run")
    }
}

// End-to-end tests that start the game, open an anvil and take screenshots:
// `./gradlew :<version>:runClientGameTest` (opens a game window). Fabric's client game test API exists from 1.21.4.
if (sc.current.parsed >= "1.21.4") {
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "enchantcalc-gametest"
            enableGameTests = false
            enableClientGameTests = true
            eula = true
        }
    }
}

// Every Minecraft release this jar supports, for the Modrinth version tags.
val minecraftReleases: List<String> = sc.properties.rawOrNull("mod", "mc_releases")
    ?.asList().orEmpty().map { it.toString() }

modrinth {
    // A placeholder lets dry runs work without a token; real uploads need MODRINTH_TOKEN.
    token = providers.environmentVariable("MODRINTH_TOKEN").orElse("dry-run")
    projectId = property("publish.modrinth") as String
    versionNumber = "$modVersion+$minecraft"
    versionName = "$modVersion for Minecraft " +
        if (minecraftReleases.size > 1) "${minecraftReleases.first()}–${minecraftReleases.last()}" else minecraft
    versionType = "release"
    uploadFile.set(loomx.modJar)
    gameVersions.addAll(minecraftReleases)
    loaders.add("fabric")
    environment = "client_only"
    changelog = provider { releaseNotes(rootProject.file("CHANGELOG.md"), modVersion) }
    dependencies {
        required.project("fabric-api")
    }
    // Nothing is uploaded unless `-Ppublish.dryRun=false` is passed.
    debugMode = providers.gradleProperty("publish.dryRun").map(String::toBoolean).orElse(true)
}

/** The CHANGELOG.md section for [version]: from its `## version` heading up to the next heading. */
fun releaseNotes(file: File, version: String): String {
    if (!file.exists()) {
        return "No changelog provided."
    }
    val lines = file.readLines()
    val start = lines.indexOfFirst { it.startsWith("## $version") }
    if (start < 0) {
        return "No changelog provided."
    }
    val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
    return lines.subList(start + 1, end).joinToString("\n").trim()
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = requiredJava.majorVersion.toInt()
    }

    processResources {
        val props = mapOf(
            "id" to modId,
            "name" to modName,
            "version" to modVersion,
            "minecraft" to minecraftCompat,
            "java" to requiredJava.majorVersion,
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
        filesMatching("*.mixins.json") { expand("java" to "JAVA_${requiredJava.majorVersion}") }
    }

    test {
        useJUnitPlatform()
    }

    withType<Jar>().configureEach {
        inputs.property("mod_id", modId)
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
    }

    // Writes this version's compile classpath to build/compile-classpath.txt, for `javap` API checks.
    register("dumpClasspath") {
        group = "help"
        description = "Writes the compile classpath of this Minecraft version to build/compile-classpath.txt"
        val cp = sourceSets.main.get().compileClasspath
        val out = layout.buildDirectory.file("compile-classpath.txt")
        doLast { out.get().asFile.writeText(cp.files.joinToString("\n")) }
    }

    // Copies the release jar of this version into the root build/libs/<mod version>/ folder.
    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the release jar and copies it to build/libs/<mod version>/"
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/$modVersion"))
    }
}
