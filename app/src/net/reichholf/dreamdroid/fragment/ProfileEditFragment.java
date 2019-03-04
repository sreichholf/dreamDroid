/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import net.reichholf.dreamdroid.DatabaseHelper;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.BaseFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.Statics;

import androidx.annotation.NonNull;

import static net.reichholf.dreamdroid.fragment.abs.BaseHttpFragment.sData;

/**
 * Used to edit connection profiles
 *
 * @author sre
 */
public class ProfileEditFragment extends BaseFragment {
	private Profile mCurrentProfile;

	private EditText mProfile;
	private EditText mHost;
	private EditText mStreamHost;
	private EditText mPort;
	private EditText mStreamPort;
	private EditText mFilePort;
	private CheckBox mSsl;
	private CheckBox mLogin;
	private CheckBox mStreamLogin;
	private CheckBox mFileSsl;
	private CheckBox mFileLogin;
	private EditText mUser;
	private EditText mPass;
	private CheckBox mSimpleRemote;
	private LinearLayout mLayoutLogin;
	private LinearLayout mLayoutStream;
	//Encoder Settings
	private CheckBox mEncoderStream;
	private CheckBox mEncoderLogin;
	private EditText mEncoderPath;
	private EditText mEncoderUser;
	private EditText mEncoderPass;
	private EditText mEncoderVideoBitrate;
	private EditText mEncoderAudioBitrate;
	private EditText mEncoderPort;
	private LinearLayout mLayoutEncoder;
	private LinearLayout mLayoutEncoderLogin;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mHasFabMain = true;
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		initTitles(getString(R.string.edit_profile));
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.profile_edit, container, false);

		mProfile = view.findViewById(R.id.EditTextProfile);
		mHost = view.findViewById(R.id.EditTextHost);
		mStreamHost = view.findViewById(R.id.EditTextStreamHost);
		mPort = view.findViewById(R.id.EditTextPort);
		mStreamPort = view.findViewById(R.id.EditTextStreamPort);
		mFilePort = view.findViewById(R.id.EditTextFilePort);
		mSsl = view.findViewById(R.id.CheckBoxSsl);
		mLogin = view.findViewById(R.id.CheckBoxLogin);
		mStreamLogin = view.findViewById(R.id.CheckBoxLoginStream);
		mUser = view.findViewById(R.id.EditTextUser);
		mPass = view.findViewById(R.id.EditTextPass);
		mSimpleRemote = view.findViewById(R.id.CheckBoxSimpleRemote);
		mFileSsl = view.findViewById(R.id.CheckBoxSslFileStream);
		mFileLogin = view.findViewById(R.id.CheckBoxLoginFileStream);

		//Encoder
		mEncoderStream = view.findViewById(R.id.CheckBoxEncoder);
		mEncoderPath = view.findViewById(R.id.EditTextEncoderPath);
		mEncoderPort = view.findViewById(R.id.EditTextEncoderPort);
		mEncoderLogin = view.findViewById(R.id.CheckBoxEncoderLogin);
		mEncoderUser = view.findViewById(R.id.EditTextEncodermUser);
		mEncoderPass = view.findViewById(R.id.EditTextEncoderPass);
		mEncoderVideoBitrate = view.findViewById(R.id.EditTextVideoBitrate);
		mEncoderAudioBitrate = view.findViewById(R.id.EditTextAudioBitrate);

		mLayoutEncoder = view.findViewById(R.id.linearLayoutEncoder);
		mLayoutEncoderLogin = view.findViewById(R.id.linearLayoutEncoderLogin);
		mLayoutStream = view.findViewById(R.id.linearLayoutStream);
		mLayoutLogin = view.findViewById(R.id.LoginLayout);

		ExtendedHashMap extras = (ExtendedHashMap) getArguments().getSerializable(sData);
		assert extras != null;

		if (Intent.ACTION_EDIT.equals(extras.get("action"))) {
			mCurrentProfile = (Profile) extras.get("profile");
			if (mCurrentProfile == null)
				mCurrentProfile = Profile.getDefault();
			assignProfile();
		}
		onIsLoginChanged(mLogin.isChecked());
		onIsEncoderStreamChanged(mEncoderStream.isChecked());
		onIsEncoderLoginChanged(mEncoderLogin.isChecked());
		registerListeners();
		return view;
	}

	private void registerListeners() {
		mLogin.setOnCheckedChangeListener((checkbox, checked) -> onIsLoginChanged(checked));

		mSsl.setOnCheckedChangeListener((checkbox, checked) -> onSslChanged(checked));

		mEncoderStream.setOnCheckedChangeListener((buttonView, isChecked) -> onIsEncoderStreamChanged(isChecked));

		mEncoderLogin.setOnCheckedChangeListener((buttonView, isChecked) -> onIsEncoderLoginChanged(isChecked));
		registerFab(R.id.fab_main, R.string.save, R.drawable.ic_action_save, v -> save());
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.save, menu);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case Statics.ITEM_SAVE:
				save();
				break;
			case Statics.ITEM_CANCEL:
				finish(Activity.RESULT_CANCELED);
				break;
			default:
				return false;
		}
		return true;
	}

	@SuppressLint("SetTextI18n")
	private void onSslChanged(boolean checked) {
		if (checked)
			mPort.setText("443");
		else
			mPort.setText("80");
	}

	/**
	 * @param checked Enables or disables the user/password input-boxes depending on
	 *                login enabled/disabled
	 */
	private void onIsLoginChanged(boolean checked) {
		if (checked) {
			mLayoutLogin.setVisibility(View.VISIBLE);
		} else {
			mLayoutLogin.setVisibility(View.GONE);
		}
	}

	private void onIsEncoderStreamChanged(boolean checked) {
		if (checked) {
			mLayoutEncoder.setVisibility(View.VISIBLE);
			mLayoutStream.setVisibility(View.GONE);
		} else {
			mLayoutEncoder.setVisibility(View.GONE);
			mLayoutStream.setVisibility(View.VISIBLE);
		}
	}

	private void onIsEncoderLoginChanged(boolean checked) {
		if (checked) {
			mLayoutEncoderLogin.setVisibility(View.VISIBLE);
		} else {
			mLayoutEncoderLogin.setVisibility(View.GONE);
		}
	}


	/**
	 * Assign all values of <code>mProfile</code> to the GUI-Components
	 */
	private void assignProfile() {
		mProfile.setText(mCurrentProfile.getName());
		mHost.setText(mCurrentProfile.getHost());
		mStreamHost.setText(mCurrentProfile.getStreamHostValue());
		mSsl.setChecked(mCurrentProfile.isSsl());
		mPort.setText(mCurrentProfile.getPortString());
		mStreamPort.setText(mCurrentProfile.getStreamPortString());
		mFilePort.setText(mCurrentProfile.getFilePortString());
		mLogin.setChecked(mCurrentProfile.isLogin());
		mStreamLogin.setChecked(mCurrentProfile.isStreamLogin());
		mUser.setText(mCurrentProfile.getUser());
		mPass.setText(mCurrentProfile.getPass());
		mFileLogin.setChecked(mCurrentProfile.isFileLogin());
		mFileSsl.setChecked(mCurrentProfile.isFileSsl());
		mSimpleRemote.setChecked(mCurrentProfile.isSimpleRemote());

		mEncoderStream.setChecked(mCurrentProfile.isEncoderStream());
		mEncoderPath.setText(mCurrentProfile.getEncoderPath());
		mEncoderPort.setText(mCurrentProfile.getEncoderPortString());
		mEncoderLogin.setChecked(mCurrentProfile.isEncoderLogin());
		mEncoderUser.setText(mCurrentProfile.getEncoderUser());
		mEncoderPass.setText(mCurrentProfile.getEncoderPass());
		mEncoderVideoBitrate.setText(mCurrentProfile.getEncoderVideoBitrateString());
		mEncoderAudioBitrate.setText(mCurrentProfile.getEncoderAudioBitrateString());
	}

	/**
	 * Save the profile permanently
	 */
	private void save() {
		mCurrentProfile.setName(mProfile.getText().toString());
		mCurrentProfile.setHost(mHost.getText().toString().trim());
		mCurrentProfile.setStreamHost(mStreamHost.getText().toString().trim());
		mCurrentProfile.setPort(mPort.getText().toString(), mSsl.isChecked());
		mCurrentProfile.setStreamPort(mStreamPort.getText().toString());
		mCurrentProfile.setFilePort(mFilePort.getText().toString());
		mCurrentProfile.setLogin(mLogin.isChecked());
		mCurrentProfile.setStreamLogin(mStreamLogin.isChecked());
		mCurrentProfile.setFileLogin(mFileLogin.isChecked());
		mCurrentProfile.setFileSsl(mFileSsl.isChecked());
		mCurrentProfile.setUser(mUser.getText().toString());
		mCurrentProfile.setPass(mPass.getText().toString());
		mCurrentProfile.setSimpleRemote(mSimpleRemote.isChecked());
		//Encoder
		mCurrentProfile.setEncoderStream(mEncoderStream.isChecked());
		mCurrentProfile.setEncoderPath(mEncoderPath.getText().toString());
		mCurrentProfile.setEncoderPort(mEncoderPort.getText().toString());
		mCurrentProfile.setEncoderLogin(mEncoderLogin.isChecked());
		mCurrentProfile.setEncoderUser(mEncoderUser.getText().toString());
		mCurrentProfile.setEncoderPass(mEncoderPass.getText().toString());
		mCurrentProfile.setEncoderAudioBitrate(mEncoderAudioBitrate.getText().toString());
		mCurrentProfile.setEncoderVideoBitrate(mEncoderVideoBitrate.getText().toString());


		Context ctx = getContext();
		if(ctx == null) { //FIMXE: why/how does this happen?
			showToast(getText(R.string.profile_not_updated) + " '" + mCurrentProfile.getName() + "'");
			return;
		}
		DatabaseHelper dbh = DatabaseHelper.getInstance(ctx);
		if (mCurrentProfile.getId() > 0) {
			if (mCurrentProfile.getHost() == null || "".equals(mCurrentProfile.getHost())) {
				showToast(getText(R.string.host_empty));
				return;
			}
			if (mCurrentProfile.getStreamHost() == null) {
				mCurrentProfile.setStreamHost("");
			}
			if (dbh.updateProfile(mCurrentProfile)) {
				showToast(getText(R.string.profile_updated) + " '" + mCurrentProfile.getName() + "'");
				finish(Activity.RESULT_OK);
			} else {
				showToast(getText(R.string.profile_not_updated) + " '" + mCurrentProfile.getName() + "'");
			}
		} else {
			if (dbh.addProfile(mCurrentProfile)) {
				showToast(getText(R.string.profile_added) + " '" + mCurrentProfile.getName() + "'");
				finish(Activity.RESULT_OK);
			} else {
				showToast(getText(R.string.profile_not_added) + " '" + mCurrentProfile.getName() + "'");
			}
		}
	}

	/**
	 * Show a toast
	 *
	 * @param toastText The text to show
	 */
	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * Show a toast
	 *
	 * @param toastText The text to show
	 */
	protected void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}
}
