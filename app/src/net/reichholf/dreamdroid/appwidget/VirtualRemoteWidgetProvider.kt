package net.reichholf.dreamdroid.appwidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.reichholf.dreamdroid.Profile

/**
 * Home-screen Virtual Remote receiver. Glance chassis ([VirtualRemoteWidget]) with
 * dense RCU via [VirtualRemoteWidgetViews] / [AndroidRemoteViews]. Same FQCN as the
 * former Java [AppWidgetProvider] so existing widgets keep working.
 */
class VirtualRemoteWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VirtualRemoteWidget()

    override fun onReceive(context: Context, intent: Intent) {
        if (WidgetRemoteRequest.ACTION_RCU == intent.action) {
            val pendingResult = goAsync()
            WidgetRemoteRequest.enqueue(context, intent) { pendingResult.finish() }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            VirtualRemoteWidgetConfiguration.deleteWidgetConfiguration(context, appWidgetId)
        }
    }

    companion object {
        const val WIDGET_PREFERENCE_PREFIX = "virtual_remote."

        private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Refresh one widget after configure. Prefers Glance update; falls back to a
         * direct [RemoteViews] push if Glance has not bound the id yet.
         */
        @JvmStatic
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            profile: Profile?,
        ) {
            if (profile == null) return
            val appContext = context.applicationContext
            updateScope.launch {
                try {
                    val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(appWidgetId)
                    VirtualRemoteWidget().update(appContext, glanceId)
                } catch (_: IllegalArgumentException) {
                    appWidgetManager.updateAppWidget(
                        appWidgetId,
                        VirtualRemoteWidgetViews.build(appContext, appWidgetId, profile),
                    )
                }
            }
        }
    }
}
