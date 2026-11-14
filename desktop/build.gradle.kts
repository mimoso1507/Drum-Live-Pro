// Windows (desktop) version of Drum Live Pro. Standalone build: run it with `../gradlew -p desktop installDist`.
// Reuses the audio engine, SF2 reader, FX, sequencer, models and icons of the Android app (see `sharedSources`).
plugins {
    kotlin("jvm") version "2.0.21"
    application
}

val appDir = rootDir.resolve("../app")
// Single source of truth for the version: the Android app's versionName.
val appVersion: String = Regex("versionName = \"([^\"]+)\"").find(appDir.resolve("build.gradle.kts").readText())!!.groupValues[1]
version = appVersion

dependencies {
    implementation("org.json:json:20240303")
}


val sharedSrc = appDir.resolve("src/main/java/com/drumlivepro/app")
val sharedFiles = listOf(
    "audio/EngineCore.kt", "audio/Sf2.kt", "audio/Effects.kt",
    "seq/PatternGen.kt", "seq/Timing.kt",
    "model/Instruments.kt", "model/Kits.kt", "model/Recording.kt",
    "ui/IconPainter.kt",
)
val sharedSources by tasks.registering(Sync::class) {
    from(sharedSrc) { sharedFiles.forEach { include(it) } }
    into(layout.buildDirectory.dir("shared-src/com/drumlivepro/app"))
}
val genResources by tasks.registering {
    val out = layout.buildDirectory.dir("gen-res")
    inputs.property("version", appVersion)
    outputs.dir(out)
    doLast { out.get().file("drumlivepro-version.txt").asFile.apply { parentFile.mkdirs(); writeText(appVersion) } }
}
sourceSets.main {
    kotlin.srcDir(layout.buildDirectory.dir("shared-src"))
    resources.srcDir(appDir.resolve("src/main/assets"))
    resources.srcDir(genResources)
}
tasks.named("compileKotlin") { dependsOn(sharedSources) }

application {
    mainClass.set("com.drumlivepro.desk.MainKt")
    applicationName = "DrumLivePro"
}
tasks.jar { archiveFileName.set("drumlivepro.jar") }

tasks.register("printVersion") { doLast { println(appVersion) } }
