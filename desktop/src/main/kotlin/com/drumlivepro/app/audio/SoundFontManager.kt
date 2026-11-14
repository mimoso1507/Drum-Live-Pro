package com.drumlivepro.app.audio

import java.io.File

/** Desktop version: keeps the installed SF2 files (bundled + imported) in the app data folder and loads them on demand. */
class SoundFontManager(baseDir: File) {
    val dir = File(baseDir, "sf2").apply { mkdirs() }
    private val headers = HashMap<String, SoundFont>()
    private val loaded = HashMap<String, SoundFont>()

    companion object { const val BUNDLED = "drumlive_kits.sf2" }

    fun ensureBundled() {
        val f = File(dir, BUNDLED)
        val bytes = javaClass.getResourceAsStream("/sf2/$BUNDLED")?.use { it.readBytes() } ?: return
        if (!f.exists() || f.length() != bytes.size.toLong()) f.writeBytes(bytes)
    }

    fun files(): List<File> = (dir.listFiles { f -> f.name.endsWith(".sf2", true) }?.toList() ?: emptyList())
        .sortedWith(compareBy({ it.name != BUNDLED }, { it.name.lowercase() }))

    @Synchronized fun header(name: String): SoundFont? = headers[name] ?: loaded[name] ?: runCatching {
        SoundFont.readHeaders(File(dir, name)).also { headers[name] = it }
    }.getOrNull()

    @Synchronized fun get(name: String): SoundFont? = loaded[name] ?: runCatching {
        SoundFont.load(File(dir, name)).also { loaded[name] = it; headers[name] = it }
    }.getOrNull()

    @Synchronized fun retainOnly(names: Set<String>) {
        loaded.keys.filter { it !in names }.forEach { loaded.remove(it) }
    }

    /** Copies a user picked SF2 into the app folder. Returns the stored file name. */
    @Synchronized fun import(src: File): String {
        var name = src.name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        if (!name.endsWith(".sf2", true)) name += ".sf2"
        val target = File(dir, name)
        src.copyTo(target, overwrite = true)
        try { SoundFont.readHeaders(target) } catch (e: Exception) { target.delete(); throw e }
        headers.remove(name); loaded.remove(name)
        return name
    }

    @Synchronized fun delete(name: String) {
        if (name == BUNDLED) return
        File(dir, name).delete(); headers.remove(name); loaded.remove(name)
    }

    fun kitPresets(name: String): List<PresetInfo> {
        val sf = header(name) ?: return emptyList()
        val drums = sf.presets.filter { it.isDrumBank }
        return (if (drums.isNotEmpty()) drums else sf.presets).sortedWith(compareBy({ it.bank }, { it.program }))
    }

    fun presetIndex(sf: SoundFont, bank: Int, program: Int): Int =
        sf.presets.firstOrNull { it.bank == bank && it.program == program }?.index
            ?: sf.presets.firstOrNull { it.isDrumBank }?.index ?: sf.presets.firstOrNull()?.index ?: -1
}
