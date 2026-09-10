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
	public void onUpdate(@NonNull Context context, @NonNull AppWidgetManager appWidgetManager,
						 @NonNull int[] appWidgetIds) {
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
		boolean playButtonAsPlayPause = PreferenceManager.getDefaultSharedPreferences(context)
				.getBoolean(DreamDroid.PREFS_KEY_PLAY_BUTTON_AS_PLAY_PAUSE, false);
		registerButtons(context, remoteViews, appWidgetId, playButtonAsPlayPause);
		remoteViews.setViewVisibility(R.id.ButtonPlay, playButtonAsPlayPause ? View.INVISIBLE : View.VISIBLE);
		remoteViews.setViewVisibility(R.id.ButtonPlayPause, playButtonAsPlayPause ? View.VISIBLE : View.INVISIBLE);
		appWidgetManager.updateAppWidget(appWidgetId, remoteViews);
	}

	public static void registerButtons(Context context, @NonNull RemoteViews remoteViews, int appWidgetId,
									   boolean playButtonAsPlayPause) {
		for (Integer[] btn : VirtualRemoteFragment.getRemoteButtons(playButtonAsPlayPause)) {
			Intent intent = new Intent(context, VirtualRemoteWidgetProvider.class);
			intent.putExtra(WidgetRemoteRequest.KEY_WIDGETID, appWidgetId);
			intent.putExtra(WidgetRemoteRequest.KEY_KEYID, Integer.toString(btn[1]));
			intent.setAction(WidgetRemoteRequest.ACTION_RCU);

			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, btn[0], intent,
					PendingIntent.FLAG_IMMUTABLE);
			remoteViews.setOnClickPendingIntent(btn[0], pendingIntent);
		}
	}

	@Override
	public void onReceive(@NonNull Context context, @NonNull Intent intent) {
		super.onReceive(context, intent);
		if (WidgetRemoteRequest.ACTION_RCU.equals(intent.getAction())) {
			final PendingResult pendingResult = goAsync();
			WidgetRemoteRequest.enqueue(context, intent, pendingResult::finish);
		}
	}
}
