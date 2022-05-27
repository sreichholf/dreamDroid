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
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.SimpleExtendedHashMapAdapter;
import net.reichholf.dreamdroid.adapter.recyclerview.SimpleTextAdapter;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.widget.helper.ItemClickSupport;
import net.reichholf.dreamdroid.widget.helper.ItemSelectionSupport;

import java.util.ArrayList;

/**
 * Created by Stephan on 07.12.13.
 */
public class VirtualRemoteWidgetConfiguration extends AppCompatActivity implements ItemClickSupport.OnItemClickListener {
    private ArrayList<Profile> mProfiles;
	private RecyclerView mRecyclerView;
	private ItemClickSupport mItemClickSupport;

    private int mAppWidgetId;


	@Override
	public void onCreate(Bundle savedInstanceState) {
		DreamDroid.setTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.virtual_remote_widget_config);
		setResult(RESULT_CANCELED);

		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null) {
			mAppWidgetId = extras.getInt(
					AppWidgetManager.EXTRA_APPWIDGET_ID,
					AppWidgetManager.INVALID_APPWIDGET_ID);
		}

		mRecyclerView = findViewById(R.id.recycler_view);
		mItemClickSupport = ItemClickSupport.addTo(mRecyclerView);
		mItemClickSupport.setOnItemClickListener(this);

		load();
	}

	public void load() {
		DatabaseHelper dbh = DatabaseHelper.getInstance(this);
        ArrayList<ExtendedHashMap> profiles = new ArrayList<>();
		mProfiles = dbh.getProfiles();
		if (mProfiles.size() > 0) {
			for (Profile m : mProfiles) {
				ExtendedHashMap map = new ExtendedHashMap();
				map.put(DatabaseHelper.KEY_PROFILE_PROFILE, m.getName());
				map.put(DatabaseHelper.KEY_PROFILE_HOST, m.getHost());
				profiles.add(map);
			}

			SimpleTextAdapter adapter = new SimpleTextAdapter(profiles, R.layout.two_line_card_list_item_no_indicator, new String[]{
                    DatabaseHelper.KEY_PROFILE_PROFILE, DatabaseHelper.KEY_PROFILE_HOST}, new int[]{android.R.id.text1,
                    android.R.id.text2});
			mRecyclerView.setAdapter(adapter);
			mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
			adapter.notifyDataSetChanged();
		} else {
			showToast(getString(R.string.no_profile_available));
			finish();
		}
	}

	private boolean isQuickZapChecked(){
        RadioGroup widgetStyleGroup = (RadioGroup) findViewById(R.id.remote_widget_style_group);
        return widgetStyleGroup.getCheckedRadioButtonId() == R.id.remote_widget_style_simple;
	}
	public void finish(int profileId, boolean isQuickZap) {
		saveWidgetConfiguration(profileId, !isQuickZap);
		Context context = getApplicationContext();
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		Profile profile = getWidgetProfile(context, mAppWidgetId);
		VirtualRemoteWidgetProvider.updateWidget(context, appWidgetManager, mAppWidgetId, profile);

		Intent data = new Intent();
		data.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, data);
		finish();
	}

	public void saveWidgetConfiguration(int profileId, boolean isFull) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(getProfileIdKey(mAppWidgetId), profileId);
		editor.putBoolean(getIsFullKey(mAppWidgetId), isFull);
		editor.apply();
	}

	public void showToast(String text) {
		Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
		toast.show();
	}

	public static Profile getWidgetProfile(Context context, int appWidgetId) {
		int profileId = PreferenceManager.getDefaultSharedPreferences(context).getInt(getProfileIdKey(appWidgetId), -1);
		DatabaseHelper dbh = DatabaseHelper.getInstance(context);
		return dbh.getProfile(profileId);
	}

	@NonNull
	public static String getProfileIdKey(int appWidgetId) {
		return VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + Integer.toString(appWidgetId);
	}

	@NonNull
	public static String getIsFullKey(int appWidgetId) {
		return VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + Integer.toString(appWidgetId) + "isFull";
	}

	public static boolean isFull(Context context, int appWidgetId) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(VirtualRemoteWidgetProvider.WIDGET_PREFERENCE_PREFIX + Integer.toString(appWidgetId) + "isFull", false);
	}

	public static void deleteWidgetConfiguration(Context context, int appWidgetId) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		if (prefs.contains(getProfileIdKey(appWidgetId))) {
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove(getProfileIdKey(appWidgetId));
			editor.apply();
		}
	}

	@Override
	public void onItemClick(RecyclerView recyclerView, View v, int position, long id) {
		finish(mProfiles.get(position).getId(), isQuickZapChecked());
	}
}
