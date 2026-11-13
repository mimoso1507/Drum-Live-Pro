package com.drummstch.deluxe.audio

import android.content.Context
import android.net.Uri
import java.io.File

/** Keeps the installed SF2 files (bundled + imported) and loads them on demand. */
class SoundFontManager(private val ctx: Context) {
    val dir = File(ctx.filesDir, "sf2").apply { mkdirs() }
    private val headers = HashMap<String, SoundFont>()
    private val loaded = HashMap<String, SoundFont>()

    companion object { const val BUNDLED = "drummstch_kits.sf2" }

    fun ensureBundled() {
        val f = File(dir, BUNDLED)
        val asset = ctx.assets.openFd("sf2/$BUNDLED").use { it.length }
        if (!f.exists() || f.length() != asset) {
            ctx.assets.open("sf2/$BUNDLED").use { inp -> f.outputStream().use { inp.copyTo(it) } }
        }
    }

    fun files(): List<File> = (dir.listFiles { f -> f.name.endsWith(".sf2", true) }?.toList() ?: emptyList())
        .sortedWith(compareBy({ it.name != BUNDLED }, { it.name.lowercase() }))

    @Synchronized fun header(name: String): SoundFont? = headers[name] ?: loaded[name] ?: runCatching {
        SoundFont.readHeaders(File(dir, name)).also { headers[name] = it }
    }.getOrNull()

    @Synchronized fun get(name: String): SoundFont? = loaded[name] ?: runCatching {
        SoundFont.load(File(dir, name)).also { loaded[name] = it; headers[name] = it }
    }.getOrNull()

    /** Frees samples of SF2s not used by the given set of names. */
    @Synchronized fun retainOnly(names: Set<String>) {
        loaded.keys.filter { it !in names }.forEach { loaded.remove(it) }
    }

    /** Copies a user picked SF2 into the private folder. Returns the stored file name. */
    fun import(uri: Uri, displayName: String?): String {
        var name = (displayName ?: "kit_${System.currentTimeMillis()}.sf2").replace(Regex("[^A-Za-z0-9._ -]"), "_")
        if (!name.endsWith(".sf2", true)) name += ".sf2"
        val target = File(dir, name)
        ctx.contentResolver.openInputStream(uri)!!.use { inp -> target.outputStream().use { inp.copyTo(it) } }
        try { SoundFont.readHeaders(target) } catch (e: Exception) { target.delete(); throw e }
        headers.remove(name); loaded.remove(name)
        return name
    }

    fun delete(name: String) {
        if (name == BUNDLED) return
        File(dir, name).delete(); headers.remove(name); loaded.remove(name)
    }

    /** Presets considered "kits": bank 128 presets, or every preset if the file has no drum bank. */
    fun kitPresets(name: String): List<PresetInfo> {
        val sf = header(name) ?: return emptyList()
        val drums = sf.presets.filter { it.isDrumBank }
        return (if (drums.isNotEmpty()) drums else sf.presets).sortedWith(compareBy({ it.bank }, { it.program }))
    }

    fun presetIndex(sf: SoundFont, bank: Int, program: Int): Int =
        sf.presets.firstOrNull { it.bank == bank && it.program == program }?.index
            ?: sf.presets.firstOrNull { it.isDrumBank }?.index ?: sf.presets.firstOrNull()?.index ?: -1
}
