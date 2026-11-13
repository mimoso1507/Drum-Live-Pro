package com.drummatch.deluxe.model

import android.content.Context
import com.drummatch.deluxe.audio.SoundFontManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Which SF2 preset a sound comes from. */
data class SoundRef(val sf2: String, val bank: Int, val program: Int) {
    fun toJson(): JSONObject = JSONObject().put("sf2", sf2).put("bank", bank).put("program", program)
    companion object {
        fun fromJson(o: JSONObject?): SoundRef? = o?.let { SoundRef(it.getString("sf2"), it.getInt("bank"), it.getInt("program")) }
    }
}

class PadConfig(
    var note: Int,
    var source: SoundRef? = null,   // null -> the kit's own source
    var icon: Icon? = null,         // null -> automatic from note
    var name: String? = null,       // null -> automatic from note
    var volume: Float = 1f,
    var pan: Float = 0f,
) {
    @Transient var autoLevel: Int = 0

    val instrument get() = Instruments.forNote(note)
    val displayIcon get() = icon ?: instrument.icon
    val displayName get() = name?.takeIf { it.isNotBlank() } ?: instrument.name
    val role get() = if (icon != null) Instruments.roleForIcon(icon!!, instrument.role) else instrument.role

    fun copy() = PadConfig(note, source, icon, name, volume, pan)
    fun toJson(): JSONObject = JSONObject().put("note", note).put("volume", volume.toDouble()).put("pan", pan.toDouble()).also {
        source?.let { s -> it.put("source", s.toJson()) }
        icon?.let { i -> it.put("icon", i.name) }
        name?.let { n -> it.put("name", n) }
    }
    companion object {
        fun fromJson(o: JSONObject) = PadConfig(
            o.getInt("note"), SoundRef.fromJson(o.optJSONObject("source")), Icon.byName(o.optString("icon", "")),
            if (o.has("name")) o.getString("name") else null, o.optDouble("volume", 1.0).toFloat(), o.optDouble("pan", 0.0).toFloat(),
        )
        fun defaults(count: Int): MutableList<PadConfig> = MutableList(count) { i ->
            PadConfig(if (i < Instruments.defaultLayout.size) Instruments.defaultLayout[i] else 35 + (i % 47))
        }
    }
}

class Kit(
    val id: String,
    var name: String,
    var icon: Icon,
    var color: Int,
    val custom: Boolean,
    var source: SoundRef,
    var pads: MutableList<PadConfig>,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name).put("icon", icon.name).put("color", color)
        .put("custom", custom).put("source", source.toJson())
        .put("pads", JSONArray().also { a -> pads.forEach { a.put(it.toJson()) } })
    companion object {
        fun fromJson(o: JSONObject): Kit {
            val pads = o.getJSONArray("pads").let { a -> MutableList(a.length()) { PadConfig.fromJson(a.getJSONObject(it)) } }
            return Kit(o.getString("id"), o.getString("name"), Icon.byName(o.optString("icon")) ?: Icon.DRUMKIT,
                o.optInt("color", KIT_COLORS[0]), o.optBoolean("custom"), SoundRef.fromJson(o.getJSONObject("source"))!!, pads)
        }
    }
}

val KIT_COLORS = intArrayOf(
    0xFF2FD6F5.toInt(), 0xFF5BE0A0.toInt(), 0xFFFFC94D.toInt(), 0xFFFF6B6B.toInt(), 0xFFB98CFF.toInt(),
    0xFFFF8FD1.toInt(), 0xFFFF9F43.toInt(), 0xFFE8E8F0.toInt(), 0xFF4D8BFF.toInt(), 0xFF9BE15D.toInt(),
)

/** Persists kits. Built-in kits come from SF2 presets; their pad layout edits are stored as overrides. */
class KitStore(ctx: Context, private val sfm: SoundFontManager) {
    private val dir = File(ctx.filesDir, "kits").apply { mkdirs() }

    private fun fileFor(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    fun builtInId(ref: SoundRef) = "sf:${ref.sf2}:${ref.bank}:${ref.program}"

    fun builtInKits(): List<Kit> = sfm.files().flatMap { f ->
        sfm.kitPresets(f.name).map { p ->
            val ref = SoundRef(f.name, p.bank, p.program)
            val nm = if (f.name == SoundFontManager.BUNDLED) p.name else "${p.name} (${f.nameWithoutExtension})"
            Kit(builtInId(ref), nm, iconForName(p.name), colorForName(p.name), false, ref, PadConfig.defaults(12))
        }
    }

    fun customKits(): List<Kit> = (dir.listFiles { f -> f.name.startsWith("custom-") }?.toList() ?: emptyList())
        .mapNotNull { runCatching { Kit.fromJson(JSONObject(it.readText())) }.getOrNull() }
        .sortedBy { it.name.lowercase() }

    fun all(): List<Kit> = builtInKits() + customKits()

    /** Returns the kit with any saved layout override applied. */
    fun load(id: String): Kit? {
        val f = fileFor(id)
        if (f.exists()) runCatching { return Kit.fromJson(JSONObject(f.readText())) }
        return builtInKits().firstOrNull { it.id == id }
    }

    fun save(kit: Kit) { fileFor(kit.id).writeText(kit.toJson().toString()) }

    fun delete(kit: Kit) { fileFor(kit.id).delete() }

    fun newCustom(name: String, icon: Icon, color: Int, source: SoundRef, pads: MutableList<PadConfig>) =
        Kit("custom-" + UUID.randomUUID().toString().take(8), name, icon, color, true, source, pads)

    companion object {
        fun iconForName(n: String): Icon {
            val s = n.lowercase()
            return when {
                "808" in s || "909" in s -> Icon.DRUM_MACHINE
                "electr" in s -> Icon.EQ
                "dance" in s -> Icon.SPARKLE
                "jazz" in s -> Icon.RIDE
                "brush" in s -> Icon.CROSS_STICKS
                "power" in s -> Icon.FIRE
                "room" in s -> Icon.SPEAKER
                "orch" in s -> Icon.TIMPANI
                "sfx" in s -> Icon.BOLT
                "perc" in s -> Icon.CONGA
                "cm-" in s -> Icon.ROBOT
                else -> Icon.DRUMKIT
            }
        }
        fun colorForName(n: String): Int = KIT_COLORS[(n.hashCode() and 0x7fffffff) % KIT_COLORS.size]
    }
}
