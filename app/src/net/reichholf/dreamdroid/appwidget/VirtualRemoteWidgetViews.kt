package net.reichholf.dreamdroid.appwidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.preference.PreferenceManager
import android.view.View
import android.widget.RemoteViews
import net.reichholf.dreamdroid.DreamDroid
import net.reichholf.dreamdroid.Profile
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.ui.remote.VirtualRemoteButtons

/**
 * Dense Virtual Remote [RemoteViews] grids. Glance hosts these via
 * [androidx.glance.appwidget.AndroidRemoteViews] (Phase 2.6e hybrid).
 */
object VirtualRemoteWidgetViews {
    @JvmStatic
    fun build(context: Context, appWidgetId: Int, profile: Profile): RemoteViews {
        val remoteViews = if (VirtualRemoteWidgetConfiguration.isFull(context, appWidgetId)) {
            RemoteViews(context.packageName, R.layout.virtual_remote_appwidget)
        } else {
            RemoteViews(context.packageName, R.layout.virtual_remote_appwidget_quickzap)
        }
        remoteViews.setTextViewText(R.id.profile_name, profile.name)
        val playAsPlayPause = PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, false)
        registerButtons(context, remoteViews, appWidgetId, playAsPlayPause)
        remoteViews.setViewVisibility(
            R.id.ButtonPlay,
            if (playAsPlayPause) View.INVISIBLE else View.VISIBLE,
        )
        remoteViews.setViewVisibility(
            R.id.ButtonPlayPause,
            if (playAsPlayPause) View.VISIBLE else View.INVISIBLE,
        )
        return remoteViews
    }

    private fun registerButtons(
        context: Context,
        remoteViews: RemoteViews,
        appWidgetId: Int,
        playAsPlayPause: Boolean,
    ) {
        for (btn in VirtualRemoteButtons.getRemoteButtons(playAsPlayPause)) {
            val intent = Intent(context, VirtualRemoteWidgetProvider::class.java).apply {
                putExtra(WidgetRemoteRequest.KEY_WIDGETID, appWidgetId)
                putExtra(WidgetRemoteRequest.KEY_KEYID, btn[1].toString())
                action = WidgetRemoteRequest.ACTION_RCU
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                btn[0],
                intent,
                PendingIntent.FLAG_IMMUTABLE,
            )
            remoteViews.setOnClickPendingIntent(btn[0], pendingIntent)
        }
    }
}
