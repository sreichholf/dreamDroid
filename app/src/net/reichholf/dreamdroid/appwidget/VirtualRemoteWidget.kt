package net.reichholf.dreamdroid.appwidget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.fillMaxSize
import net.reichholf.dreamdroid.R

/**
 * Glance Virtual Remote widget. Dense RCU grids remain XML [RemoteViews]
 * embedded through [AndroidRemoteViews] (Phase 2.6e hybrid).
 */
class VirtualRemoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(context, appWidgetId)
        val remoteViews = if (profile == null) {
            RemoteViews(context.packageName, R.layout.virtual_remote_appwidget_quickzap).apply {
                setTextViewText(
                    R.id.profile_name,
                    context.getString(R.string.no_profile_available),
                )
            }
        } else {
            VirtualRemoteWidgetViews.build(context, appWidgetId, profile)
        }
        provideContent {
            VirtualRemoteGlanceContent(remoteViews)
        }
    }
}

@Composable
private fun VirtualRemoteGlanceContent(remoteViews: RemoteViews) {
    AndroidRemoteViews(
        remoteViews = remoteViews,
        modifier = GlanceModifier.fillMaxSize(),
    )
}
