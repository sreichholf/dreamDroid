/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.abstivities.MultiPaneHandler;
import net.reichholf.dreamdroid.helpers.Statics;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
/**
 * Used to edit connection profiles
 *
 * @author sre
 *
 */
public class ProfileEditFragment extends SherlockFragment implements ActivityCallbackHandler{
	private Profile mCurrentProfile;

	private EditText mProfile;
	private EditText mHost;
	private EditText mStreamHost;
	private EditText mPort;
	private EditText mStreamPort;
	private CheckBox mSsl;
	private CheckBox mLogin;
	private EditText mUser;
	private EditText mPass;
	private CheckBox mSimpleRemote;
	private LinearLayout mLayoutLogin;

	private Activity mActivity;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mActivity = getSherlockActivity();
		mActivity.setTitle(getString(R.string.edit_profile));
		mActivity.setProgressBarIndeterminateVisibility(false);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){
		View view = inflater.inflate(R.layout.profile_edit, container, false);

		mProfile = (EditText) view.findViewById(R.id.EditTextProfile);
		mHost = (EditText) view.findViewById(R.id.EditTextHost);
		mStreamHost = (EditText) view.findViewById(R.id.EditTextStreamHost);
		mPort = (EditText) view.findViewById(R.id.EditTextPort);
		mStreamPort = (EditText) view.findViewById(R.id.EditTextStreamPort);
		mSsl = (CheckBox) view.findViewById(R.id.CheckBoxSsl);
		mLogin = (CheckBox) view.findViewById(R.id.CheckBoxLogin);
		mUser = (EditText) view.findViewById(R.id.EditTextUser);
		mPass = (EditText) view.findViewById(R.id.EditTextPass);
		mSimpleRemote = (CheckBox) view.findViewById(R.id.CheckBoxSimpleRemote);

		mLayoutLogin = (LinearLayout) view.findViewById(R.id.LinearLayoutLogin);

		mLogin.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton checkbox, boolean checked) {
				onIsLoginChanged(checked);
			}
		});
		
		mSsl.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton checkbox, boolean checked) {
				onSslChanged(checked);
			}
		});
		
		if (Intent.ACTION_EDIT.equals(getArguments().getString("action"))) {
			mCurrentProfile = (Profile) getArguments().getSerializable("profile");
			if (mCurrentProfile == null)
				mCurrentProfile = new Profile();
			assignProfile();
		}
		onIsLoginChanged(mLogin.isChecked());
		return view;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.save, menu);
		inflater.inflate(R.menu.cancel, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
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
	
	private void onSslChanged(boolean checked){
		if(checked)
			mPort.setText("443");
		else
			mPort.setText("80");
	}
	
	/**
	 * @param checked
	 *            Enables or disables the user/password input-boxes depending on
	 *            login enabled/disabled
	 */
	private void onIsLoginChanged(boolean checked) {
		if (checked) {
			mLayoutLogin.setVisibility(View.VISIBLE);
		} else {
			mLayoutLogin.setVisibility(View.GONE);
		}
	}

	/**
	 * Assign all values of <code>mProfile</code> to the GUI-Components
	 */
	private void assignProfile() {
		mProfile.setText(mCurrentProfile.getName());
		mHost.setText(mCurrentProfile.getHost());
		mStreamHost.setText(mCurrentProfile.getStreamHostValue());
		mPort.setText(mCurrentProfile.getPortString());
		mStreamPort.setText(mCurrentProfile.getStreamPortString());
		mSsl.setChecked(mCurrentProfile.isSsl());
		mLogin.setChecked(mCurrentProfile.isLogin());
		mUser.setText(mCurrentProfile.getUser());
		mPass.setText(mCurrentProfile.getPass());
		mSimpleRemote.setChecked(mCurrentProfile.isSimpleRemote());
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
		mCurrentProfile.setLogin(mLogin.isChecked());
		mCurrentProfile.setUser(mUser.getText().toString());
		mCurrentProfile.setPass(mPass.getText().toString());
		mCurrentProfile.setSimpleRemote(mSimpleRemote.isChecked());

		if (mCurrentProfile.getId() > 0) {
			if (mCurrentProfile.getHost() == null || "".equals(mCurrentProfile.getHost())) {
				showToast(getText(R.string.host_empty));
				return;
			}
			if (mCurrentProfile.getStreamHost() == null) {
				mCurrentProfile.setStreamHost("");
			}
			if (DreamDroid.updateProfile(mCurrentProfile)) {
				showToast(getText(R.string.profile_updated) + " '" + mCurrentProfile.getName() + "'");
				finish(Activity.RESULT_OK);
			} else {
				showToast(getText(R.string.profile_not_updated) + " '" + mCurrentProfile.getName() + "'");
			}
		} else {
			if (DreamDroid.addProfile(mCurrentProfile)) {
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
	 * @param toastText
	 *            The text to show
	 */
	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(mActivity, toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	/**
	 * Show a toast
	 *
	 * @param toastText
	 *            The text to show
	 */
	protected void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(mActivity, toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	@Override
	public Dialog onCreateDialog(int id) {
		return null;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}

	/**
	 * If a targetFragment has been set using setTargetFragement() return to it.
	 */
	protected void finish(int resultCode){
		MultiPaneHandler mph = (MultiPaneHandler) getSherlockActivity();
		if(mph.isMultiPane()){
			Fragment f = getTargetFragment();
			if(f != null){
				mph.showDetails(f);
				f.onActivityResult(getTargetRequestCode(), resultCode, null);
			}
		} else {
			Activity a = getSherlockActivity();
			a.setResult(resultCode);
			a.finish();
		}
	}
}
