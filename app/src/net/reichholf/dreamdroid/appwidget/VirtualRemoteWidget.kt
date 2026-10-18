package net.reichholf.dreamdroid.appwidget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.fillMaxSize
import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.ProfileRepository

/**
 * Glance Virtual Remote widget. Dense RCU grids remain XML [RemoteViews]
 * embedded through [AndroidRemoteViews] (Phase 2.6e hybrid).
 */
class VirtualRemoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val profiles = WidgetEntryPoint.get(context).profileRepository()
        val initial = remoteViews(context, profiles, appWidgetId)
        provideContent {
            // A running session only recomposes on update(), it does not call provideGlance
            // again: rebuild when the configure activity bumps the revision.
            val revision = currentState(CONFIG_REVISION) ?: 0
            val remoteViews by produceState(initial, revision) {
                value = remoteViews(context, profiles, appWidgetId)
            }
            VirtualRemoteGlanceContent(remoteViews)
        }
    }

    private suspend fun remoteViews(
        context: Context,
        profiles: ProfileRepository,
        appWidgetId: Int
    ): RemoteViews {
        val profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(
            context,
            profiles,
            appWidgetId
        ) ?: return RemoteViews(
            context.packageName,
            R.layout.virtual_remote_appwidget_quickzap
        ).apply {
            setTextViewText(R.id.profile_name, context.getString(R.string.no_profile_available))
        }
        return VirtualRemoteWidgetViews.build(context, appWidgetId, profile)
    }

    companion object {
        private val CONFIG_REVISION = intPreferencesKey("config_revision")

        /** Redraws [id] after its configuration in the shared preferences changed. */
        suspend fun reconfigure(context: Context, id: GlanceId) {
            updateAppWidgetState(context, id) { state ->
                state[CONFIG_REVISION] = (state[CONFIG_REVISION] ?: 0) + 1
            }
            VirtualRemoteWidget().update(context, id)
        }
    }
}

@Composable
private fun VirtualRemoteGlanceContent(remoteViews: RemoteViews) {
    AndroidRemoteViews(
        remoteViews = remoteViews,
        modifier = GlanceModifier.fillMaxSize()
    )
}
