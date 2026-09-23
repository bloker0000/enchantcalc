plugins {
    id("dev.kikugie.stonecutter")
    id("com.modrinth.minotaur")
}

// The Minecraft version `src/` is currently written for (what the IDE sees).
// Switch with the "Set active project to ..." Gradle tasks; commit with it set to `vcsVersion`.
stonecutter active "26.3"

stonecutter parameters {
    // Pure renames. `src/` uses the newest names and older versions get them rewritten back;
    // anything that changed shape uses `//? if` blocks in the code instead.
    replacements {
        string(current.parsed >= "26.1") {
            replace("GuiGraphics", "GuiGraphicsExtractor")
            replace("renderWidget", "extractWidgetRenderState")
            replace("Screens.getButtons", "Screens.getWidgets")
        }
        string(current.parsed >= "1.21.11") {
            replace("ResourceLocation", "Identifier")
        }
    }
}

// Upload versions oldest first, so the newest one is on top on Modrinth.
stonecutter tasks {
    order("modrinth")
}

// The root project only syncs the project page from README.md; every version uploads its own jar.
modrinth {
    // A placeholder lets dry runs work without a token; real uploads need MODRINTH_TOKEN.
    token = providers.environmentVariable("MODRINTH_TOKEN").orElse("dry-run")
    projectId = property("publish.modrinth") as String
    syncBodyFrom = provider { rootProject.file("README.md").readText() }
    debugMode = providers.gradleProperty("publish.dryRun").map(String::toBoolean).orElse(true)
}

tasks.named("modrinth") {
    enabled = false
}

tasks.register("publishModrinth") {
    group = "publishing"
    description = "Uploads every version to Modrinth and syncs the project page. A dry run unless -Ppublish.dryRun=false."
    dependsOn(stonecutter.tasks.named("modrinth"), tasks.named("modrinthSyncBody"))
}
