package net.reichholf.dreamdroid.appwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.VirtualRemoteFragment;

/**
 * Created by Stephan on 07.12.13.
 */
public class VirtualRemoteWidgetProvider extends AppWidgetProvider {
    public static final String WIDGET_PREFERENCE_PREFIX = "virtual_remote.";

    @Override
    public void onUpdate(@NonNull Context context, @NonNull AppWidgetManager appWidgetManager, @NonNull int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            Profile profile = VirtualRemoteWidgetConfiguration.getWidgetProfile(context, appWidgetId);
            updateWidget(context, appWidgetManager, appWidgetId, profile);
        }
    }

    @Override
    public void onDeleted(Context context, @NonNull int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            VirtualRemoteWidgetConfiguration.deleteWidgetConfiguration(context, appWidgetId);
        }
    }

    public static void updateWidget(@NonNull Context context, @NonNull AppWidgetManager appWidgetManager,
									int appWidgetId, @Nullable Profile profile) {

        if (profile == null)
            return;
        RemoteViews remoteViews;
        if (VirtualRemoteWidgetConfiguration.isFull(context, appWidgetId))
            remoteViews = new RemoteViews(context.getPackageName(), R.layout.virtual_remote_appwidget);
        else
            remoteViews = new RemoteViews(context.getPackageName(), R.layout.virtual_remote_appwidget_quickzap);
        remoteViews.setTextViewText(R.id.profile_name, profile.getName());
        boolean mPlayButtonAsPlayPause = PreferenceManager.getDefaultSharedPreferences(context).getBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, false);
        registerButtons(context, remoteViews, appWidgetId, profile, mPlayButtonAsPlayPause);
        // Tell the AppWidgetManager to perform an update on the current app widget
        remoteViews.setViewVisibility(R.id.ButtonPlay, mPlayButtonAsPlayPause ? View.INVISIBLE : View.VISIBLE);
        remoteViews.setViewVisibility(R.id.ButtonPlayPause, mPlayButtonAsPlayPause ? View.VISIBLE : View.INVISIBLE);
        appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
    }

    public static void registerButtons(Context context, @NonNull RemoteViews remoteViews, int appWidgetId, Profile profile, boolean mPlayButtonAsPlayPause) {
        for (Integer[] btn : VirtualRemoteFragment.getRemoteButtons(mPlayButtonAsPlayPause)) {
            Intent intent = new Intent(context, VirtualRemoteWidgetProvider.class);
            intent.putExtra(WidgetService.KEY_WIDGETID, appWidgetId);
            intent.putExtra(WidgetService.KEY_KEYID, Integer.toString(btn[1]));
            intent.setAction(WidgetService.ACTION_RCU);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, btn[0], intent, PendingIntent.FLAG_IMMUTABLE);
            remoteViews.setOnClickPendingIntent(btn[0], pendingIntent);
        }
    }

    @Override
    public void onReceive(@NonNull Context context, @NonNull Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (action.equals(WidgetService.ACTION_RCU) || action.equals(WidgetService.ACTION_ZAP))
            WidgetService.enqueueWork(context, WidgetService.class, WidgetService.JOB_ID, intent);
    }
}
