/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.compose.ui.platform.ComposeView;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.asynctask.SimpleResultTask;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.SimpleResultRequestHandler;
import net.reichholf.dreamdroid.room.AppDatabase;
import net.reichholf.dreamdroid.ui.profiles.ProfileListItem;
import net.reichholf.dreamdroid.ui.share.ShareProfilesListState;
import net.reichholf.dreamdroid.ui.share.ShareProfilesListStateKt;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Share / view intent → pick a profile (Compose) → play on the box via MEDIA_PLAYER_PLAY.
 */
public class ShareActivity extends AppCompatActivity implements SimpleResultTask.SimpleResultTaskHandler {
	@NonNull
	public static String LOG_TAG = ShareActivity.class.getSimpleName();

	private SimpleResultTask mSimpleResultTask;
	private SimpleHttpClient mShc;
	private ShareProfilesListState mListState;
	@Nullable
	private ProgressDialog mProgress;
	private String mTitle;

	private List<Profile> mProfiles;
	private final Map<Integer, Profile> mProfilesById = new HashMap<>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		DreamDroid.setTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.share_list_content);
		setTitle(getText(R.string.watch_on_dream));
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		mListState = new ShareProfilesListState();
		ComposeView compose = findViewById(R.id.compose_profiles);
		ShareProfilesListStateKt.bindShareProfilesScreen(
				compose,
				mListState,
				item -> {
					Profile profile = mProfilesById.get(item.getId());
					if (profile != null) {
						playOnDream(profile);
					}
					return kotlin.Unit.INSTANCE;
				}
		);
		load();
	}

	@Override
	public void onDestroy() {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		if (mSimpleResultTask != null)
			mSimpleResultTask.cancel(true);
		super.onDestroy();
	}

	@SuppressWarnings("deprecation")
	private void playOnDream(@NonNull Profile p) {
		String url = null;
		Intent i = getIntent();
		Bundle extras = i.getExtras();
		mShc = SimpleHttpClient.getInstance(p);
		if (Intent.ACTION_SEND.equals(i.getAction()))
			url = extras.getString(Intent.EXTRA_TEXT);
		else if (Intent.ACTION_VIEW.equals(i.getAction()))
			url = i.getDataString();

		if (url != null) {
			Log.i(LOG_TAG, url);
			Log.i(LOG_TAG, p.getHost());

			String time = DateFormat.getDateFormat(this).format(new Date());
			String title = getString(R.string.sent_from_dreamdroid, time);
			if (extras != null) {
				// semperVidLinks sends "artist" and "song" attributes for the
				// youtube video titles
				String song = extras.getString("song");
				if (song != null) {
					String artist = extras.getString("artist");
					if (artist != null)
						title = artist + " - " + song;
				} else {
					String tmp = extras.getString("title");
					if (tmp != null)
						title = tmp;
				}
			}
			mTitle = title;

			Uri uri = Uri.parse(url);
			url = URLEncoder.encode(url).replace("+", "%20");
			title = URLEncoder.encode(title).replace("+", "%20");

			String ref = "4097:0:1:0:0:0:0:0:0:0:" + url + ":" + title;

			if ("youtu.be".equals(uri.getHost())) {
				String vid = uri.getPath().substring(1);
				ref = String.format("8193:0:1:0:0:0:0:0:0:0:%s:%s", URLEncoder.encode(String.format("yt://%s", vid)), title);
			}
			Log.i(LOG_TAG, ref);
			ArrayList<NameValuePair> params = new ArrayList<>();
			params.add(new NameValuePair("file", ref));
			execSimpleResultTask(params);
		} else {
			finish();
		}
	}

	public void load() {
		Profile.ProfileDao dao = AppDatabase.profiles(getContext());
		mProfiles = dao.getProfiles();
		mProfilesById.clear();
		if (mProfiles.size() > 1) {
			ArrayList<ProfileListItem> items = new ArrayList<>();
			for (Profile m : mProfiles) {
				int id = m.getId() == null ? 0 : m.getId();
				mProfilesById.put(id, m);
				items.add(new ProfileListItem(id, m.getName(), m.getHost(), false));
			}
			mListState.replaceAll(items);
		} else {
			if (mProfiles.size() == 1) {
				playOnDream(mProfiles.get(0));
			} else {
				showToast(getString(R.string.no_profile_available));
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void execSimpleResultTask(ArrayList<NameValuePair> params) {
		if (mSimpleResultTask != null) {
			mSimpleResultTask.cancel(true);
		}
		mProgress = ProgressDialog.show(this, getString(R.string.loading), getString(R.string.loading));
		SimpleResultRequestHandler handler = new SimpleResultRequestHandler(URIStore.MEDIA_PLAYER_PLAY);
		mSimpleResultTask = new SimpleResultTask(handler, this);
		mSimpleResultTask.execute(params);
	}

	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}

		if (mTitle == null)
			mTitle = "...";
		String toastText = getString(R.string.sent_as, mTitle);
		if (mShc.hasError()) {
			toastText = mShc.getErrorText(this);
		}

		showToast(toastText);
		finish();
	}

	public void showToast(String text) {
		Toast toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
		toast.show();
	}

	@NonNull
	@Override
	public Context getContext() {
		return this;
	}
}
