package net.reichholf.dreamdroid.ui.screenshot

import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.loadScreenshot
import net.reichholf.dreamdroid.helpers.NameValuePair
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.GregorianCalendar

/**
 * Screenshot grab type / format constants (formerly on ScreenShotFragment).
 */
object ScreenshotParams {
    const val TYPE_OSD = 0
    const val TYPE_VIDEO = 1
    const val TYPE_ALL = 2
    const val FORMAT_JPG = 0
    const val FORMAT_PNG = 1
}

/**
 * Optional external reload trigger for embeds (Virtual Remote tablet pane).
 */
class ScreenshotReloadTrigger {
    var tick by mutableIntStateOf(0)
        private set

    fun requestReload() {
        tick++
    }
}

/**
 * Phase 2.7c: Screenshot drawer leaf as a direct Compose NavHost destination.
 * Also embedded under Virtual Remote on large screens ([setTitle]=false, [actionsEnabled]=false).
 */
@Composable
fun ScreenshotDestination(
    type: Int = ScreenshotParams.TYPE_ALL,
    format: Int = ScreenshotParams.FORMAT_JPG,
    size: Int = -1,
    actionsEnabled: Boolean = true,
    setTitle: Boolean = true,
    reloadTrigger: ScreenshotReloadTrigger? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState = remember {
        ScreenshotUiState().apply { this.actionsEnabled = actionsEnabled }
    }
    var rawImage by remember { mutableStateOf(ByteArray(0)) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var filename by remember { mutableStateOf<String?>(null) }
    var scanner by remember { mutableStateOf<MediaScannerConnection?>(null) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun setToolbarTitle() {
        if (!setTitle) {
            return
        }
        (context as? AppCompatActivity)?.title = context.getText(R.string.screenshot)
    }

    fun fileExtension(): String = when (format) {
        ScreenshotParams.FORMAT_JPG -> "jpg"
        ScreenshotParams.FORMAT_PNG -> "png"
        else -> ""
    }

    fun mimeType(): String = when (format) {
        ScreenshotParams.FORMAT_JPG -> "jpeg"
        ScreenshotParams.FORMAT_PNG -> "png"
        else -> ""
    }

    fun onAvailable(bytes: ByteArray) {
        rawImage = bytes
        uiState.bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        uiState.loading = false
    }

    fun buildParams(): ArrayList<NameValuePair> {
        val params = ArrayList<NameValuePair>()
        when (type) {
            ScreenshotParams.TYPE_OSD -> {
                params.add(NameValuePair("o", " "))
                params.add(NameValuePair("n", " "))
            }
            ScreenshotParams.TYPE_VIDEO -> params.add(NameValuePair("v", " "))
            ScreenshotParams.TYPE_ALL -> Unit
        }
        when (format) {
            ScreenshotParams.FORMAT_JPG -> params.add(NameValuePair("format", "jpg"))
            ScreenshotParams.FORMAT_PNG -> params.add(NameValuePair("format", "png"))
        }
        if (size > 0) {
            params.add(NameValuePair("r", size.toString()))
        }
        val ts = GregorianCalendar().timeInMillis / 1000
        val shotFilename = "/tmp/dreamDroid-$ts"
        filename = shotFilename
        params.add(NameValuePair("filename", shotFilename))
        return params
    }

    fun reload() {
        uiState.loading = true
        loadJob?.cancel()
        loadJob = scope.launch {
            val result = loadScreenshot(context.applicationContext, buildParams())
            uiState.loading = false
            if (result.success && result.bytes != null) {
                onAvailable(result.bytes)
            } else {
                toast(result.errorText?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.error))
            }
        }
    }

    fun saveToFile(inCache: Boolean): File? {
        val bytes = rawImage
        if (bytes.isEmpty()) {
            return null
        }
        val extension = fileExtension()
        if (inCache) {
            val fileName = "dreamDroid.$extension"
            return try {
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                file
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }
        val timestamp = GregorianCalendar.getInstance().timeInMillis
        val fileName = "dreamDroid_$timestamp.$extension"
        val imageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        }
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val resolver = context.applicationContext.contentResolver
        val imageContentUri = resolver.insert(imageCollection, imageDetails) ?: return null
        return try {
            val pfd: ParcelFileDescriptor = resolver.openFileDescriptor(imageContentUri, "w")!!
            FileOutputStream(pfd.fileDescriptor).use { it.write(bytes) }
            pfd.close()
            toast(context.getString(R.string.screenshot_saved, fileName))
            null
        } catch (e: IOException) {
            Log.e(DreamDroid.LOG_TAG, e.localizedMessage ?: e.toString())
            toast(e.toString())
            null
        }
    }

    fun share() {
        val file = saveToFile(true) ?: run {
            toast(context.getString(R.string.error))
            return
        }
        file.setReadable(true, false)
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".provider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            setType("image/${mimeType()}")
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    DisposableEffect(Unit) {
        setToolbarTitle()
        val conn = MediaScannerConnection(context, object : MediaScannerConnection.MediaScannerConnectionClient {
            override fun onMediaScannerConnected() {}
            override fun onScanCompleted(path: String?, uri: Uri?) {}
        })
        conn.connect()
        scanner = conn
        onDispose {
            loadJob?.cancel()
            loadJob = null
            conn.disconnect()
            scanner = null
        }
    }

    LaunchedEffect(Unit) {
        if (rawImage.isEmpty()) {
            reload()
        } else {
            onAvailable(rawImage)
        }
    }

    val triggerTick = reloadTrigger?.tick ?: 0
    LaunchedEffect(triggerTick) {
        if (triggerTick > 0) {
            reload()
        }
    }

    ScreenshotScreen(
        state = uiState,
        onReload = { reload() },
        onShare = { share() },
        onSave = { saveToFile(false) },
        modifier = modifier,
    )
}
