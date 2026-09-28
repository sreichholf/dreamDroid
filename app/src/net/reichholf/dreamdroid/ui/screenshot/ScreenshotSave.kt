package net.reichholf.dreamdroid.ui.screenshot

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import net.reichholf.dreamdroid.DreamDroid

/** Writes [image] to the device's pictures; returns the file name, or null on failure. */
internal fun saveScreenshotToGallery(context: Context, image: ByteArray): String? {
    val fileName = "dreamDroid_${System.currentTimeMillis()}.jpg"
    val details = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val resolver = context.applicationContext.contentResolver
    val uri = resolver.insert(collection, details) ?: return null
    return try {
        resolver.openOutputStream(uri)?.use { it.write(image) } ?: return null
        fileName
    } catch (e: IOException) {
        Log.e(DreamDroid.LOG_TAG, e.localizedMessage ?: e.toString())
        null
    }
}

/** Opens the share sheet for [image]; false when the file to share could not be written. */
internal fun shareScreenshot(context: Context, image: ByteArray): Boolean {
    val file = try {
        File(context.cacheDir, "dreamDroid.jpg").apply { writeBytes(image) }
    } catch (e: IOException) {
        Log.e(DreamDroid.LOG_TAG, e.localizedMessage ?: e.toString())
        return false
    }
    val uri = FileProvider.getUriForFile(
        context,
        context.applicationContext.packageName + ".provider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_STREAM, uri)
        type = "image/jpeg"
    }
    context.startActivity(Intent.createChooser(intent, null))
    return true
}
