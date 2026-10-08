package com.example.verde.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Copies a scan photo into the phone's shared storage: Pictures/Verde.
 * It then shows up in the Gallery app as a "Verde" album.
 *
 * Uses MediaStore (Android 10 and newer), which needs no storage permission.
 * On older phones (Android 7–9) it skips saving and the photo stays only inside the app.
 */
object GallerySaver {

    /** Saves [photo] as "[name].jpg" in Pictures/Verde. Answers true if it was saved. */
    fun save(context: Context, photo: File, name: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(context, photo, name)
        else false

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveWithMediaStore(context: Context, photo: File, name: String): Boolean {
        val resolver = context.contentResolver

        // 1. Describe the new picture: name, type, folder. IS_PENDING hides it until it's fully written.
        val details = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Verde")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        // 2. Ask Android for a place to put it
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, details) ?: return false

        return try {
            // 3. Copy the photo's bytes into that place
            val output = resolver.openOutputStream(uri) ?: throw IllegalStateException("No output stream")
            output.use { out -> photo.inputStream().use { it.copyTo(out) } }

            // 4. Finished writing: make it visible in the Gallery
            details.clear()
            details.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, details, null, null)
            true
        } catch (e: Exception) {
            Log.e("Verde", "Saving to Pictures/Verde failed", e)
            resolver.delete(uri, null, null)        // don't leave a broken, empty picture behind
            false
        }
    }
}
