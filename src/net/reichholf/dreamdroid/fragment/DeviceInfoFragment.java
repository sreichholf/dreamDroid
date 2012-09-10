/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpFragment;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.DeviceInfo;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.CurrentServiceRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.DeviceInfoRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncSimpleLoader;
import android.app.Activity;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.commonsware.cwac.merge.MergeAdapter;

/**
 * Shows device-specific information for the active profile.
 * 
 * @author sreichholf
 * 
 */
public class DeviceInfoFragment extends AbstractHttpFragment {
	private ExtendedHashMap mInfo;
	private MergeAdapter mMerge;
	private SimpleAdapter mFrontendAdapter;
	private SimpleAdapter mNicAdapter;
	private SimpleAdapter mHddAdapter;
	private TextView mGuiVersion;
	private TextView mImageVersion;
	private TextView mInterfaceVersion;
	private TextView mFrontprocessorVersion;
	private TextView mDeviceName;
	private ArrayList<ExtendedHashMap> mFrontends;
	private ArrayList<ExtendedHashMap> mNics;
	private ArrayList<ExtendedHashMap> mHdds;
	private SimpleHttpClient mShc;
	private LayoutInflater mInflater;
	private ListView mList;

	private Activity mActivity;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mActivity = getSherlockActivity();
		mActivity.setProgressBarIndeterminateVisibility(false);
		getSherlockActivity().setTitle(getText(R.string.device_info));
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// <taken-from-android-list-fragment>
		FrameLayout root = new FrameLayout(getSherlockActivity());

		TextView tv = new TextView(getSherlockActivity());
		tv.setId(android.R.id.empty);
		tv.setGravity(Gravity.CENTER);
		root.addView(tv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
				ViewGroup.LayoutParams.FILL_PARENT));

		mList = new ListView(getSherlockActivity());
		mList.setId(android.R.id.list);
		mList.setDrawSelectorOnTop(false);
		root.addView(mList, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
				ViewGroup.LayoutParams.FILL_PARENT));

		ListView.LayoutParams lp = new ListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
				ViewGroup.LayoutParams.FILL_PARENT);
		root.setLayoutParams(lp);
		// </taken-from-android-list-fragment>

		mMerge = new MergeAdapter();
		mInfo = new ExtendedHashMap();
		mFrontends = new ArrayList<ExtendedHashMap>();
		mNics = new ArrayList<ExtendedHashMap>();
		mHdds = new ArrayList<ExtendedHashMap>();

		mInflater = getLayoutInflater(savedInstanceState);
		ScrollView fields = (ScrollView) mInflater.inflate(R.layout.device_info, null);

		mMerge.addView(fields);

		mGuiVersion = (TextView) fields.findViewById(R.id.GuiVersion);
		mImageVersion = (TextView) fields.findViewById(R.id.ImageVersion);
		mInterfaceVersion = (TextView) fields.findViewById(R.id.InterfaceVersion);
		mFrontprocessorVersion = (TextView) fields.findViewById(R.id.FrontprocessorVersion);
		mDeviceName = (TextView) fields.findViewById(R.id.DeviceName);
		setClient();

		setAdapter();
		mList.setAdapter(mMerge);

		if (savedInstanceState == null) {
			reload();
		} else {
			mInfo = (ExtendedHashMap) savedInstanceState.getParcelable("info");
			onInfoReady();
		}

		return root;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("info", mInfo);
	}

	/**
	 * @param id
	 *            ID of the R.string to be set as the content of the
	 *            <code>DreamDroid.R.layout.simple_header</code>
	 * @return
	 */
	private TextView getListHeaderView(int id) {
		TextView header = (TextView) mInflater.inflate(R.layout.simple_header, null);
		header.setText(id);
		return header;
	}

	/**
	 * Set all required stuff for the
	 * <code>com.commonsware.cwac.merge.MergeAdapter</code>
	 */
	private void setAdapter() {
		mFrontendAdapter = new SimpleAdapter(mActivity, mFrontends, android.R.layout.two_line_list_item, new String[] {
				DeviceInfo.KEY_FRONTEND_NAME, DeviceInfo.KEY_FRONTEND_MODEL }, new int[] { android.R.id.text1,
				android.R.id.text2 });

		mMerge.addView(getListHeaderView(R.string.frontends));
		mMerge.addAdapter(mFrontendAdapter);

		mNicAdapter = new SimpleAdapter(mActivity, mNics, android.R.layout.two_line_list_item, new String[] {
				DeviceInfo.KEY_NIC_NAME, DeviceInfo.KEY_NIC_IP }, new int[] { android.R.id.text1, android.R.id.text2 });

		mMerge.addView(getListHeaderView(R.string.nics));
		mMerge.addAdapter(mNicAdapter);

		mHddAdapter = new SimpleAdapter(mActivity, mHdds, android.R.layout.two_line_list_item, new String[] {
				DeviceInfo.KEY_HDD_MODEL, DeviceInfo.KEY_HDD_CAPACITY }, new int[] { android.R.id.text1,
				android.R.id.text2 });

		mMerge.addView(getListHeaderView(R.string.hdds));
		mMerge.addAdapter(mHddAdapter);
	}

	/**
	 * Called when device info has been loaded and parsed successfully
	 */
	@SuppressWarnings("unchecked")
	private void onInfoReady() {
		mFrontends.clear();
		mFrontends.addAll((ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.KEY_FRONTENDS));

		mNics.clear();
		mNics.addAll((ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.KEY_NICS));

		mHdds.clear();
		mHdds.addAll((ArrayList<ExtendedHashMap>) mInfo.get(DeviceInfo.KEY_HDDS));

		mGuiVersion.setText(mInfo.getString(DeviceInfo.KEY_GUI_VERSION));
		mImageVersion.setText(mInfo.getString(DeviceInfo.KEY_IMAGE_VERSION));
		mInterfaceVersion.setText(mInfo.getString(DeviceInfo.KEY_INTERFACE_VERSION));
		mFrontprocessorVersion.setText(mInfo.getString(DeviceInfo.KEY_FRONT_PROCESSOR_VERSION));
		mDeviceName.setText(mInfo.getString(DeviceInfo.KEY_DEVICE_NAME));

		mMerge.notifyDataSetChanged();
	}

	@Override
	public Loader<ExtendedHashMap> onCreateLoader(int id, Bundle args) {
		AsyncSimpleLoader loader = new AsyncSimpleLoader(getSherlockActivity(), new DeviceInfoRequestHandler(), args);
		return loader;
	}

	/*
	 * You want override this if you don't override onLoadFinished!
	 */
	protected void applyData(int loaderId, ExtendedHashMap content) {
		if (content != null) {
			mInfo = content;
			onInfoReady();
		}
	}
}
