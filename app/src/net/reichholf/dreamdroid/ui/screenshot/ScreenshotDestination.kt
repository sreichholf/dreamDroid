package net.reichholf.dreamdroid.ui.screenshot

import android.content.ContentValues
import android.content.Intent
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.GregorianCalendar
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.nav.PhoneNavHandle
import net.reichholf.dreamdroid.ui.nav.runOnlineOnly
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

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
 * The load and [ScreenshotUiState] live on [ScreenshotViewModel].
 */
@Composable
fun ScreenshotDestination(
    type: Int = ScreenshotParams.TYPE_ALL,
    format: Int = ScreenshotParams.FORMAT_JPG,
    size: Int = -1,
    actionsEnabled: Boolean = true,
    setTitle: Boolean = true,
    reloadTrigger: ScreenshotReloadTrigger? = null,
    handle: PhoneNavHandle? = null,
    modifier: Modifier = Modifier,
    viewModel: ScreenshotViewModel = viewModel()
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val status by SessionConnectionHolder.shared.status.collectAsState()
    val blocked = status.blocksMutations
    var scanner by remember { mutableStateOf<MediaScannerConnection?>(null) }
    viewModel.uiState.actionsEnabled = actionsEnabled

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun setToolbarTitle() {
        if (!setTitle) {
            return
        }
        (context as? AppCompatActivity)?.title = resources.getText(R.string.screenshot)
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

    fun toastGallerySaveError() {
        toast(resources.getString(R.string.error))
    }

    fun failGallerySave(bytes: ByteArray, inserted: Boolean, ioFailed: Boolean): Boolean {
        if (screenshotGallerySaveError(bytes, inserted, ioFailed) == null) {
            return false
        }
        toastGallerySaveError()
        return true
    }

    fun saveToFile(inCache: Boolean): File? {
        val bytes = viewModel.rawImage
        val extension = fileExtension()
        if (inCache) {
            if (bytes.isEmpty()) {
                return null
            }
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
        if (failGallerySave(bytes, inserted = true, ioFailed = false)) {
            return null
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
        val imageContentUri = resolver.insert(imageCollection, imageDetails)
        if (failGallerySave(bytes, inserted = imageContentUri != null, ioFailed = false) ||
            imageContentUri == null
        ) {
            return null
        }
        return try {
            val pfd: ParcelFileDescriptor = resolver.openFileDescriptor(imageContentUri, "w")!!
            FileOutputStream(pfd.fileDescriptor).use { it.write(bytes) }
            pfd.close()
            toast(resources.getString(R.string.screenshot_saved, fileName))
            null
        } catch (e: IOException) {
            Log.e(DreamDroid.LOG_TAG, e.localizedMessage ?: e.toString())
            failGallerySave(bytes, inserted = true, ioFailed = true)
            null
        }
    }

    fun share() {
        val file = saveToFile(true) ?: run {
            toast(resources.getString(R.string.error))
            return
        }
        file.setReadable(true, false)
        val uri = FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            setType("image/${mimeType()}")
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    DisposableEffect(Unit) {
        setToolbarTitle()
        val conn =
            MediaScannerConnection(
                context,
                object : MediaScannerConnection.MediaScannerConnectionClient {
                    override fun onMediaScannerConnected() {}
                    override fun onScanCompleted(path: String?, uri: Uri?) {}
                }
            )
        conn.connect()
        scanner = conn
        onDispose {
            conn.disconnect()
            scanner = null
        }
    }

    val error = viewModel.errorText
    LaunchedEffect(error) {
        if (!error.isNullOrEmpty()) {
            toast(error)
            viewModel.consumeError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.start(type, format, size)
    }

    val triggerTick = reloadTrigger?.tick ?: 0
    LaunchedEffect(triggerTick) {
        if (triggerTick > 0) {
            viewModel.reload(type, format, size)
        }
    }

    ScreenshotScreen(
        state = viewModel.uiState,
        grabBlocked = blocked,
        onReload = {
            if (handle != null) {
                handle.runOnlineOnly { viewModel.reload(type, format, size) }
            } else if (!blocked) {
                viewModel.reload(type, format, size)
            }
        },
        onShare = { share() },
        onSave = { saveToFile(false) },
        modifier = modifier
    )
}
