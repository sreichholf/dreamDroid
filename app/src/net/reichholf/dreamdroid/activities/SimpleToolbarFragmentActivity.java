package net.reichholf.dreamdroid.activities;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.WindowManager;

import net.reichholf.dreamdroid.R;

public class SimpleToolbarFragmentActivity extends SimpleFragmentActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        super.onCreate(savedInstanceState);
    }

    @Override
    @SuppressWarnings("ConstantConditions") // titleResource must not be null
    protected void initViews(boolean initFragment) {
        super.initViews(initFragment);

        setContentView(R.layout.simple_layout_with_toolbar);
        Integer titleResource = (Integer) getIntent().getExtras().get("titleResource");
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(titleResource);
    }
}
