package net.reichholf.dreamdroid.ui.screenshot

import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes and shares through the real MediaStore and FileProvider. Every gallery entry a test
 * inserts is deleted again in a `finally`, so a failed assertion leaves nothing behind.
 *
 * `shareScreenshot` ends with `startActivity`, which an application context may only call with
 * `FLAG_ACTIVITY_NEW_TASK`; the share tests therefore pass a [ContextWrapper] that records the
 * intent instead of launching the chooser.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotSaveTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.applicationContext.contentResolver

    @Test
    fun saveWritesThePngAndReturnsTheNameItInserted() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val png = pngBytes()
        val fileName = saveScreenshotToGallery(context, png)
        try {
            val name = requireNotNull(fileName) { "saveScreenshotToGallery returned null" }
            assertArrayEquals(png, readBytes(onlyEntryNamed(name)))
        } finally {
            deleteEntriesNamed(fileName)
        }
    }

    @Test
    fun saveWritesBytesThatAreNotAnImageVerbatim() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val notAnImage = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val fileName = saveScreenshotToGallery(context, notAnImage)
        try {
            val name = requireNotNull(fileName) {
                "saveScreenshotToGallery rejected bytes that are not an image"
            }
            assertArrayEquals(notAnImage, readBytes(onlyEntryNamed(name)))
        } finally {
            deleteEntriesNamed(fileName)
        }
    }

    @Test
    fun shareWritesTheCacheFileAndStartsAChooser() {
        val png = pngBytes()
        val cacheFile = File(context.cacheDir, "dreamDroid.jpg")
        cacheFile.delete()
        var started: Intent? = null
        val recording = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent) {
                started = intent
            }
        }
        try {
            assertTrue(shareScreenshot(recording, png))
            assertArrayEquals(png, cacheFile.readBytes())
            val chooser = requireNotNull(started) { "startActivity was not called" }
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val sendIntent = IntentCompat.getParcelableExtra(
                chooser,
                Intent.EXTRA_INTENT,
                Intent::class.java
            )
            val send = requireNotNull(sendIntent) { "chooser carries no send intent" }
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("image/jpeg", send.type)
            val stream = IntentCompat.getParcelableExtra(
                send,
                Intent.EXTRA_STREAM,
                Uri::class.java
            )
            assertNotNull(stream)
        } finally {
            cacheFile.delete()
        }
    }

    @Test
    fun shareReturnsFalseWhenTheCacheFileCannotBeWritten() {
        val blocker = File(context.cacheDir, "dreamDroid.jpg")
        blocker.delete()
        try {
            assertTrue("could not create a directory at the cache file path", blocker.mkdirs())
            assertFalse(shareScreenshot(context, pngBytes()))
        } finally {
            blocker.delete()
        }
    }

    private fun pngBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val out = ByteArrayOutputStream()
        try {
            assertTrue(
                "PNG compression failed",
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            )
        } finally {
            bitmap.recycle()
        }
        return out.toByteArray()
    }

    private fun imagesCollection(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    /** The entry URIs MediaStore holds under [name]; only this app's own entries are visible. */
    private fun entryUrisNamed(name: String): List<Uri> {
        val collection = imagesCollection()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val uris = mutableListOf<Uri>()
        resolver.query(collection, projection, selection, arrayOf(name), null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                uris += ContentUris.withAppendedId(collection, cursor.getLong(idColumn))
            }
        }
        return uris
    }

    private fun onlyEntryNamed(name: String): Uri {
        val uris = entryUrisNamed(name)
        assertEquals("MediaStore entries named $name", 1, uris.size)
        return uris.first()
    }

    private fun readBytes(uri: Uri): ByteArray {
        val stream = requireNotNull(resolver.openInputStream(uri)) { "no stream for $uri" }
        return stream.use { it.readBytes() }
    }

    private fun deleteEntriesNamed(name: String?) {
        if (name == null) {
            return
        }
        entryUrisNamed(name).forEach { resolver.delete(it, null, null) }
    }
}
