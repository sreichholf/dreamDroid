package net.reichholf.dreamdroid.tv.activities;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;

import net.reichholf.dreamdroid.R;

/**
 * Created by Stephan on 26.10.2016.
 */

public class PreferenceActivity extends FragmentActivity {
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tv_preferences_main);
		setTitle(getString(R.string.settings));
	}
}
