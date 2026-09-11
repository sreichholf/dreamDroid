package net.reichholf.dreamdroid.tv.fragment.abs;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.leanback.app.BrowseSupportFragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.OnItemViewClickedListener;
import androidx.leanback.widget.OnItemViewSelectedListener;

/**
 * Created by Stephan on 16.10.2016.
 */

public abstract class BaseHttpBrowseFragment extends BrowseSupportFragment implements OnItemViewSelectedListener, OnItemViewClickedListener {

	public static final int LOADER_DEFAULT_ID = 0;

	@NonNull
	protected ArrayObjectAdapter mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setAdapter(mRowsAdapter);
	}
}
