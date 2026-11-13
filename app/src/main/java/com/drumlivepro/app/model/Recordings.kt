package com.drumlivepro.app.model

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.drumlivepro.app.audio.NoteEvent
import com.drumlivepro.app.seq.Meter
import com.drumlivepro.app.seq.PPQ
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

class Recording(val name: String, val bpm: Double, val meter: Meter, val bars: Int, val kitId: String?, val events: List<NoteEvent>, val created: Long) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("bpm", bpm).put("meter", meter.toString()).put("bars", bars)
        .put("kit", kitId).put("created", created)
        .put("events", JSONArray().also { a -> events.forEach { e -> a.put(JSONArray().put(e.tick).put(e.pad).put(e.note).put(e.vel)) } })

    companion object {
        fun fromJson(o: JSONObject): Recording {
            val a = o.getJSONArray("events")
            val ev = List(a.length()) { val x = a.getJSONArray(it); NoteEvent(x.getInt(0), x.getInt(1), x.getInt(2), x.getInt(3)) }
            return Recording(o.getString("name"), o.getDouble("bpm"), Meter.parse(o.getString("meter")), o.getInt("bars"),
                o.optString("kit").ifEmpty { null }, ev, o.optLong("created"))
        }
    }
}

class RecordingStore(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "recordings").apply { mkdirs() }
    private fun file(name: String) = File(dir, name.replace(Regex("[^A-Za-z0-9 ._-]"), "_") + ".json")

    fun list(): List<Recording> = (dir.listFiles()?.toList() ?: emptyList())
        .mapNotNull { runCatching { Recording.fromJson(JSONObject(it.readText())) }.getOrNull() }
        .sortedByDescending { it.created }

    fun save(r: Recording) = file(r.name).writeText(r.toJson().toString())
    fun delete(r: Recording) = file(r.name).delete()
    fun exists(name: String) = file(name).exists()

    /** Writes a file to the public Downloads/DrumLivePro folder (or app folder on old Android). Returns a readable location. */
    fun exportPublic(fileName: String, mime: String, write: (OutputStream) -> Unit): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DrumLivePro")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("No se pudo crear el archivo")
            ctx.contentResolver.openOutputStream(uri)!!.use(write)
            return "Descargas/DrumLivePro/$fileName"
        }
        val d = File(ctx.getExternalFilesDir(null), "export").apply { mkdirs() }
        val f = File(d, fileName)
        f.outputStream().use(write)
        return f.absolutePath
    }

    /** Standard MIDI File (type 0) on channel 10, so it opens in any DAW with a GM drum map. */
    fun toMidi(r: Recording, repeats: Int = 1): ByteArray {
        val track = ByteArrayOutputStream()
        fun vlq(v: Int) {
            var buf = v and 0x7f; var x = v shr 7
            while (x > 0) { buf = buf shl 8; buf = buf or ((x and 0x7f) or 0x80); x = x shr 7 }
            while (true) { track.write(buf and 0xff); if (buf and 0x80 != 0) buf = buf shr 8 else break }
        }
        val mpqn = (60_000_000 / r.bpm).toInt()
        vlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x51, 3, (mpqn shr 16).toByte(), (mpqn shr 8).toByte(), mpqn.toByte()))
        val dd = Integer.numberOfTrailingZeros(r.meter.den)
        vlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x58, 4, r.meter.num.toByte(), dd.toByte(), 24, 8))
        val loopTicks = r.bars * r.meter.barTicks
        val msgs = ArrayList<Triple<Int, Int, Int>>() // tick, status/data
        for (rep in 0 until repeats) for (e in r.events) {
            val t = rep * loopTicks + e.tick
            msgs += Triple(t, 0x99, (e.note shl 8) or e.vel)
            msgs += Triple(t + PPQ / 8, 0x89, (e.note shl 8))
        }
        msgs.sortWith(compareBy({ it.first }, { it.second }))
        var last = 0
        for ((t, st, d) in msgs) {
            vlq(t - last); last = t
            track.write(st); track.write(d shr 8); track.write(d and 0xff)
        }
        vlq(0); track.write(byteArrayOf(0xFF.toByte(), 0x2F, 0))
        val body = track.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray()); out.write(byteArrayOf(0, 0, 0, 6, 0, 0, 0, 1, (PPQ shr 8).toByte(), PPQ.toByte()))
        out.write("MTrk".toByteArray())
        out.write(byteArrayOf((body.size shr 24).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()))
        out.write(body)
        return out.toByteArray()
    }
}
