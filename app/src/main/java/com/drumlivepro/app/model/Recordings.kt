package com.drumlivepro.app.model

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONObject
import java.io.File
import java.io.OutputStream

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
    fun toMidi(r: Recording, repeats: Int = 1): ByteArray = MidiWriter.toMidi(r, repeats)
}
