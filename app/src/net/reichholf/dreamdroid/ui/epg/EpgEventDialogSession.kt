package net.reichholf.dreamdroid.ui.epg

import android.app.ProgressDialog
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.enigma.Event
import net.reichholf.dreamdroid.enigma.launchSimpleResultLoad
import net.reichholf.dreamdroid.fragment.PhoneNavHostFragment
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog
import net.reichholf.dreamdroid.fragment.dialogs.EpgDetailBottomSheet
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.Statics
import net.reichholf.dreamdroid.helpers.enigma2.Event as EventKeys
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult
import net.reichholf.dreamdroid.helpers.enigma2.Timer
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerAddByEventIdRequestHandler
import net.reichholf.dreamdroid.intents.IntentFactory
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler

/**
 * Shared EPG detail-sheet actions for bouquet / service / search destinations.
 */
class EpgEventDialogSession : ActionDialog.DialogActionListener {
    var hostFragment: PhoneNavHostFragment? = null
    var context: android.content.Context? = null
    var currentItem: ExtendedHashMap? = null
    private var progress: ProgressDialog? = null

    fun showDetail(event: Event) {
        val ctx = context ?: return
        currentItem = EpgListMapper.toExtendedHashMap(event)
        val sheet = EpgDetailBottomSheet.newInstance(event)
        (ctx as MultiPaneHandler).showDialogFragment(sheet, "epg_detail_dialog")
    }

    fun dismissProgress() {
        progress?.takeIf { it.isShowing }?.dismiss()
        progress = null
    }

    override fun onDialogAction(action: Int, details: Any?, dialogTag: String?) {
        val host = hostFragment ?: return
        val ctx = context ?: return
        val item = currentItem ?: return
        when (action) {
            Statics.ACTION_SET_TIMER -> {
                dismissProgress()
                progress = ProgressDialog.show(ctx, "", ctx.getText(R.string.saving), true)
                host.launchSimpleResultLoad(
                    TimerAddByEventIdRequestHandler(),
                    Timer.getEventIdParams(item),
                ) { _, result, http ->
                    dismissProgress()
                    var toastText = ctx.getText(R.string.get_content_error).toString()
                    val stateText = result.getString(SimpleResult.KEY_STATE_TEXT)
                    when {
                        !stateText.isNullOrEmpty() -> toastText = stateText
                        http.hasError() -> toastText = http.getErrorText(ctx).orEmpty()
                    }
                    Toast.makeText(ctx, toastText, Toast.LENGTH_LONG).show()
                }
            }
            Statics.ACTION_EDIT_TIMER -> {
                host.navigateToTimerEdit(Timer.createByEvent(item), true)
            }
            Statics.ACTION_FIND_SIMILAR -> {
                host.navigateToEpgSearch(item.getString(EventKeys.KEY_EVENT_TITLE))
            }
            Statics.ACTION_IMDB -> {
                val activity = ctx as? AppCompatActivity ?: return
                IntentFactory.queryIMDb(activity, item)
            }
        }
    }
}
