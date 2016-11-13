package net.reichholf.dreamdroid.tv.fragment.abs;

import android.os.Bundle;
import android.support.v17.leanback.app.BrowseSupportFragment;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;

import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;

/**
 * Created by Stephan on 16.10.2016.
 */

public abstract class BaseHttpBrowseFragment extends BrowseSupportFragment implements OnItemViewSelectedListener, OnItemViewClickedListener,
		LoaderManager.LoaderCallbacks<LoaderResult<ArrayList<ExtendedHashMap>>> {

	public static int LOADER_DEFAULT_ID = HttpFragmentHelper.LOADER_DEFAULT_ID;

	protected ArrayObjectAdapter mRowsAdapter = new ArrayObjectAdapter(new ListRowPresenter());

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setAdapter(mRowsAdapter);
	}

	@Override
	public void onLoaderReset(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader) {
	}
}
