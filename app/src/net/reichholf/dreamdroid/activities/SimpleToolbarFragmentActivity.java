package net.reichholf.dreamdroid.activities;

import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.view.WindowManager;

import net.reichholf.dreamdroid.R;

public class SimpleToolbarFragmentActivity extends SimpleFragmentActivity {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void initViews(boolean initFragment) {
		super.initViews(initFragment);

		setContentView(R.layout.simple_layout_with_toolbar);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		//noinspection ConstantConditions
		getSupportActionBar().setHomeButtonEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@Override
	protected void handleExtras(Bundle extras) {
		super.handleExtras(extras);

		if (extras.getInt("titleResource", -1) != -1) {
			//noinspection ConstantConditions
			getSupportActionBar().setTitle(extras.getInt("titleResource"));
		}
	}
}
