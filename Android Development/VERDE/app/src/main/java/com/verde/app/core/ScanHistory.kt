package com.verde.app.core

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saves every scan on the phone, newest first.
 * The list is stored as JSON in SharedPreferences; each photo is copied into the app's
 * private "history" folder so it survives the next scan.
 */
class ScanHistory(context: Context) {

    private val prefs = context.getSharedPreferences("scan_history", Context.MODE_PRIVATE)
    private val photoDir = File(context.filesDir, "history").apply { mkdirs() }

    fun all(): List<ScanRecord> {
        val json = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
    }

    fun latest(): ScanRecord? = all().firstOrNull()

    fun find(id: Long): ScanRecord? = all().firstOrNull { it.id == id }

    /** Saves a new scan and a copy of its photo. Returns the saved record. */
    fun add(answer: ScanAnswer, photo: File, fromPi: Boolean): ScanRecord {
        val id = System.currentTimeMillis()
        val savedPhoto = File(photoDir, "scan_$id.${photo.extension.ifEmpty { "jpg" }}")
        if (photo.exists()) photo.copyTo(savedPhoto, overwrite = true)   // the Pi may send no photo

        val record = ScanRecord(id, answer, savedPhoto.path, fromPi)
        val records = listOf(record) + all()
        records.drop(MAX_RECORDS).forEach { File(it.photoPath).delete() }   // keep storage small
        save(records.take(MAX_RECORDS))
        return record
    }

    /** The user said the AI was wrong: store their answer instead. */
    fun correct(id: Long, wasteClass: WasteClass) {
        save(all().map { if (it.id == id) it.correctedTo(wasteClass) else it })
    }

    fun clear() {
        all().forEach { File(it.photoPath).delete() }
        prefs.edit { remove(KEY_RECORDS) }
    }

    private fun save(records: List<ScanRecord>) {
        val array = JSONArray()
        records.forEach { array.put(toJson(it)) }
        prefs.edit { putString(KEY_RECORDS, array.toString()) }
    }

    private fun toJson(record: ScanRecord) = JSONObject()
        .put("id", record.id)
        .put("class", record.wasteClass.name)
        .put("confidence", record.confidence)
        .put("uncertain", record.answer.uncertain)
        .put("steps", JSONArray(record.answer.steps))
        .put("warnings", JSONArray(record.answer.warnings))
        .put("why", record.answer.whyItMatters)
        .put("timeMs", record.answer.timeMs)
        .put("photo", record.photoPath)
        .put("fromPi", record.fromPi)
        .put("corrected", record.corrected)

    private fun fromJson(json: JSONObject): ScanRecord? = runCatching {
        val answer = ScanAnswer(
            wasteClass = WasteClass.valueOf(json.getString("class")),
            confidence = json.optInt("confidence"),
            uncertain = json.optBoolean("uncertain"),
            steps = json.optJSONArray("steps").toStrings(),
            warnings = json.optJSONArray("warnings").toStrings(),
            whyItMatters = json.optString("why").ifEmpty { null },
            timeMs = if (json.has("timeMs")) json.getLong("timeMs") else null
        )
        ScanRecord(
            id = json.getLong("id"),
            answer = answer,
            photoPath = json.getString("photo"),
            fromPi = json.optBoolean("fromPi"),
            corrected = json.optBoolean("corrected")
        )
    }.getOrNull()

    private fun JSONArray?.toStrings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    companion object {
        private const val KEY_RECORDS = "records"
        private const val MAX_RECORDS = 100
    }
}
