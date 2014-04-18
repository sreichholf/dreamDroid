package net.reichholf.dreamdroid.appwidget;

import android.app.ListActivity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;

import java.util.ArrayList;

/**
 * Created by Stephan on 07.12.13.
 */
public class VirtualRemoteWidgetConfiguration extends ListActivity {
	private ArrayList<ExtendedHashMap> mProfileMapList;
	private ArrayList<Profile> mProfiles;
	private SimpleAdapter mAdapter;
	private int mAppWidgetId;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setResult(RESULT_CANCELED);

		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
			mAppWidgetId = extras.getInt(
					AppWidgetManager.EXTRA_APPWIDGET_ID,
					AppWidgetManager.INVALID_APPWIDGET_ID);
		}

		load();
	}

	public void load() {
		DatabaseHelper dbh = DatabaseHelper.getInstance(this);
		mProfileMapList = new ArrayList<ExtendedHashMap>();
		mProfileMapList.clear();
		mProfiles = dbh.getProfiles();
		if (mProfiles.size() > 1) {
			for (Profile m : mProfiles) {
				ExtendedHashMap map = new ExtendedHashMap();
				map.put(DatabaseHelper.KEY_PROFILE_PROFILE, m.getName());
				map.put(DatabaseHelper.KEY_PROFILE_HOST, m.getHost());
				mProfileMapList.add(map);
			}

			mAdapter = new SimpleAdapter(this, mProfileMapList, android.R.layout.two_line_list_item, new String[]{
					DatabaseHelper.KEY_PROFILE_PROFILE, DatabaseHelper.KEY_PROFILE_HOST}, new int[]{android.R.id.text1,
					android.R.id.text2});
			setListAdapter(mAdapter);
			mAdapter.notifyDataSetChanged();
		} else {
			if (mProfiles.size() == 1) {
				finish(mProfiles.get(0).getId());
			} else {
				showToast(getString(R.string.no_profile_available));
				finish();
			}
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		finish(mProfiles.get(position).getId());
	}

	public void finish(int profileId) {
		Intent data = new Intent();
		data.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, data);
		saveWidgetConfiguration(profileId);

		Context context = getApplicationContext();
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		Profile profile = getWidgetProfile(context, mAppWidgetId);
		VirtualRemoteWidgetProvider.updateWidget(context, appWidgetManager, mAppWidgetId, profile);

		finish();
	}

	public void saveWidgetConfiguration(int profileId) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(getPrefsKey(mAppWidgetId), profileId);
		editor.commit();
	}

	public void showToast(String text) {
		Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
		toast.show();
	}

	public static Profile getWidgetProfile(Context context, int appWidgetId) {
		int profileId = PreferenceManager.getDefaultSharedPreferences(context).getInt(getPrefsKey(appWidgetId), -1);
		DatabaseHelper dbh = DatabaseHelper.getInstance(context);
		Profile p = dbh.getProfile(profileId);
		return p;
	}

	public static String getPrefsKey(int appWidgetId) {
		return VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + Integer.toString(appWidgetId);
	}

	public static void deleteWidgetConfiguration(Context context, int appWidgetId) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.contains(getPrefsKey(appWidgetId))) {
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove(getPrefsKey(appWidgetId));
			editor.commit();
		}
	}
}
