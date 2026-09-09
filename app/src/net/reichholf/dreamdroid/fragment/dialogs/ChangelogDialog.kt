package net.reichholf.dreamdroid.fragment.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.dialogs.bindChangelogScreen
import org.apache.commons.io.IOUtils
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Changelog markdown. Compose scroll host with Markwon via AndroidView.
 */
class ChangelogDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val host = this
        val markdown = changelog
        val composeView = ComposeView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setViewTreeLifecycleOwner(host)
            setViewTreeViewModelStoreOwner(host)
            setViewTreeSavedStateRegistryOwner(host)
            bindChangelogScreen(markdown)
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.changelog)
            .setView(composeView)
            .setPositiveButton(R.string.close) { _, _ -> dismiss() }
            .create()
    }

    private val changelog: String
        get() {
            var text = ""
            val input = resources.openRawResource(R.raw.changelog)
            try {
                val baos = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var length: Int
                while (input.read(buffer).also { length = it } != -1) {
                    baos.write(buffer, 0, length)
                }
                text = baos.toString("UTF-8")
            } catch (e: IOException) {
                e.printStackTrace()
            }
            IOUtils.closeQuietly(input)
            return text
        }

    companion object {
        @JvmStatic
        fun newInstance(): ChangelogDialog = ChangelogDialog()
    }
}
