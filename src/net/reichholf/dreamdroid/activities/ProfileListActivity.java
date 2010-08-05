/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

/**
 * @author sre
 * 
 */
public class ProfileListActivity extends ListActivity {
	private SimpleCursorAdapter mAdapter;
	private Profile mProfile;
	private Cursor mCursor;
	public static final int DIALOG_PROFILE_ID = 0;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mCursor = DreamDroid.getProfiles();

		// the profile-table is initial - let's add the current config as
		// default Profile
		// TODO Revert to 0 before commit!
		if (mCursor.getCount() <= 1) {
			String host = DreamDroid.SP.getString("host", "dm8000");
			int port = new Integer(DreamDroid.SP.getString("port", "80"));
			String user = DreamDroid.SP.getString("user", "root");
			String pass = DreamDroid.SP.getString("pass", "dreambox");

			boolean login = DreamDroid.SP.getBoolean("login", false);
			boolean ssl = DreamDroid.SP.getBoolean("ssl", false);

			String profile = "Default";
			Profile p = new Profile(profile, host, port, login, user, pass, ssl);
			DreamDroid.addProfile(p);
			mCursor.requery();
		}

		mAdapter = new SimpleCursorAdapter(this,
				android.R.layout.two_line_list_item, mCursor, new String[] {
						DreamDroid.KEY_PROFILE, DreamDroid.KEY_HOST },
				new int[] { android.R.id.text1, android.R.id.text2 });
		setListAdapter(mAdapter);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.ListActivity#onListItemClick(android.widget.ListView,
	 * android.view.View, int, long)
	 */
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		mProfile = new Profile(mCursor);
		showDialog(DIALOG_PROFILE_ID);
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	protected Dialog onCreateDialog(int id) {
		Dialog dialog = null;
		switch (id) {
		case (DIALOG_PROFILE_ID):
			CharSequence[] actions = { getText(R.string.edit),
					getText(R.string.delete) };

			AlertDialog.Builder adBuilder = new AlertDialog.Builder(this);
			adBuilder.setTitle(getText(R.string.pick_action));
			adBuilder.setItems(actions, new DialogInterface.OnClickListener() {

				public void onClick(DialogInterface dialog, int which) {
					switch (which) {
					case 0:
						break;

					case 1:
						DreamDroid.deleteProfile(mProfile);
						
						mProfile = null;
						mCursor.requery();
						mAdapter.notifyDataSetChanged();
						break;
					}
				}
			});

			dialog = adBuilder.create();
			break;

		}
		return dialog;

	}
}
