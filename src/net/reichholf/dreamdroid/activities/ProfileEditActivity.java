/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.activities;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.Profile;
import net.reichholf.dreamdroid.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * @author sre
 * 
 */
public class ProfileEditActivity extends Activity {
	private Profile mCurrentProfile;

	private EditText mProfile;
	private EditText mHost;
	private EditText mPort;
	private CheckBox mSsl;
	private CheckBox mLogin;
	private EditText mUser;
	private EditText mPass;
	private Button mSave;
	private Button mCancel;
	private LinearLayout mLayoutLogin;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);


		setContentView(R.layout.profile_edit);

		mProfile = (EditText) findViewById(R.id.EditTextProfile);
		mHost = (EditText) findViewById(R.id.EditTextHost);
		mPort = (EditText) findViewById(R.id.EditTextPort);
		mSsl = (CheckBox) findViewById(R.id.CheckBoxSsl);
		mLogin = (CheckBox) findViewById(R.id.CheckBoxLogin);
		mUser = (EditText) findViewById(R.id.EditTextUser);
		mPass = (EditText) findViewById(R.id.EditTextPass);

		mLayoutLogin = (LinearLayout) findViewById(R.id.LinearLayoutLogin);
		mSave = (Button) findViewById(R.id.ButtonSave);
		mCancel = (Button) findViewById(R.id.ButtonCancel);
		
		mLogin.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(CompoundButton checkbox,
					boolean checked) {
				onIsLoginChanged(checked);
			}

		});
		
		mSave.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {				
				save();
			}
			
		});
		
		mCancel.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setResult(Activity.RESULT_CANCELED);
				finish();
				
			}
			
		});
				
		if (Intent.ACTION_EDIT.equals(getIntent().getAction())) {
			mCurrentProfile = (Profile) getIntent().getSerializableExtra(
					"profile");
			
			if(mCurrentProfile == null){
				mCurrentProfile = new Profile();
			} else {
				assignProfile();
			}
		}
				
		onIsLoginChanged(mLogin.isChecked());
	}
		
	/**
	 * @param checked
	 */
	private void onIsLoginChanged(boolean checked) {
		if (checked) {
			mLayoutLogin.setVisibility(View.VISIBLE);
		} else {
			mLayoutLogin.setVisibility(View.INVISIBLE);
		}
	}

	/**
	 * 
	 */
	private void assignProfile() {
		mProfile.setText(mCurrentProfile.getProfile());
		mHost.setText(mCurrentProfile.getHost());
		mPort.setText(mCurrentProfile.getPortString());
		mSsl.setChecked(mCurrentProfile.isSsl());
		mLogin.setChecked(mCurrentProfile.isLogin());
		mUser.setText(mCurrentProfile.getUser());
		mPass.setText(mCurrentProfile.getPass());
	}

	/**
	 * 
	 */
	private void save() {
		mCurrentProfile.setProfile(mProfile.getText().toString());
		mCurrentProfile.setHost(mHost.getText().toString().trim());
		mCurrentProfile.setPort(mPort.getText().toString(), mSsl.isChecked());		
		mCurrentProfile.setLogin(mLogin.isChecked());
		mCurrentProfile.setUser(mUser.getText().toString());
		mCurrentProfile.setPass(mPass.getText().toString());
		
		if(mCurrentProfile.getId() > 0){
			if(mCurrentProfile.getHost() == null || "".equals(mCurrentProfile.getHost())){
				showToast( getText(R.string.host_empty) );
				return;
			}
			if(DreamDroid.updateProfile(mCurrentProfile)){
				showToast( getText(R.string.profile_updated) + " '" + mCurrentProfile.getProfile() + "'");
				setResult(Activity.RESULT_OK);
				finish();
			} else {
				showToast( getText(R.string.profile_not_updated) + " '" + mCurrentProfile.getProfile() + "'");
			}
		} else {
			if(DreamDroid.addProfile(mCurrentProfile)){
				showToast( getText(R.string.profile_added) + " '" + mCurrentProfile.getProfile() + "'");
				setResult(Activity.RESULT_OK);
				finish();
			} else {
				showToast( getText(R.string.profile_not_added) + " '" + mCurrentProfile.getProfile() + "'");
			}
		}
	}
	
	/**
	 * @param toastText
	 */
	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();
	}
	
	/**
	 * @param toastText
	 */
	protected void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(this, toastText, Toast.LENGTH_LONG);
		toast.show();
	}
}
