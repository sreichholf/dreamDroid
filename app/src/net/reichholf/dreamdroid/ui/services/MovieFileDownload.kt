package net.reichholf.dreamdroid.ui.services

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.helpers.EnigmaHttp
import net.reichholf.dreamdroid.helpers.EnigmaHttpResult
import net.reichholf.dreamdroid.helpers.NameValuePair
import net.reichholf.dreamdroid.helpers.enigma2.URIStore

internal sealed class MovieFileDownload {
    class Ready(val file: File) : MovieFileDownload()

    class HttpFailed(val result: EnigmaHttpResult.Failure) : MovieFileDownload()

    object IoFailed : MovieFileDownload()
}

internal fun downloadMovieFile(
    context: Context,
    profile: Profile,
    remotePath: String
): MovieFileDownload {
    val appContext = context.applicationContext
    val params = listOf(NameValuePair("file", remotePath))
    return when (val fetched = EnigmaHttp(profile).fetch(URIStore.FILE, params)) {
        is EnigmaHttpResult.Failure -> MovieFileDownload.HttpFailed(fetched)
        is EnigmaHttpResult.Success -> writeMovieBytes(appContext, remotePath, fetched.bytes)
    }
}

internal fun movieViewIntent(context: Context, file: File): Intent {
    val appContext = context.applicationContext
    val uri = FileProvider.getUriForFile(
        appContext,
        appContext.packageName + ".provider",
        file
    )
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun writeMovieBytes(
    context: Context,
    remotePath: String,
    bytes: ByteArray
): MovieFileDownload = try {
    val out = File(context.cacheDir, movieCacheFileName(remotePath))
    out.writeBytes(bytes)
    MovieFileDownload.Ready(out)
} catch (_: IOException) {
    MovieFileDownload.IoFailed
}

private fun movieCacheFileName(remotePath: String): String {
    val base = remotePath.substringAfterLast('/')
    if (base.isEmpty() || base == "." || base == "..") {
        return "movie"
    }
    return base
}
