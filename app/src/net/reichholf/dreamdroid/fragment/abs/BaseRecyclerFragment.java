package net.reichholf.dreamdroid.fragment.abs;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.livefront.bridge.Bridge;

import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.MainActivity;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.helper.FragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.IBaseFragment;
import net.reichholf.dreamdroid.fragment.interfaces.IMutliPaneContent;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.widget.helper.ItemClickSupport;
import net.reichholf.dreamdroid.widget.helper.ItemSelectionSupport;
import net.reichholf.dreamdroid.widget.helper.SpacesItemDecoration;

/**
 * @author Stephan
 */
public abstract class BaseRecyclerFragment extends Fragment implements ActivityCallbackHandler, IMutliPaneContent, IBaseFragment, ItemClickSupport.OnItemClickListener, ItemClickSupport.OnItemLongClickListener, SwipeRefreshLayout.OnRefreshListener, ActionDialog.DialogActionListener {

	private FragmentHelper mHelper;
	protected boolean mHasFabMain;
	protected boolean mEnableReload = true;
	protected boolean mShouldRetainInstance = true;
	protected boolean mCardListStyle = false;

	protected ItemClickSupport mItemClickSupport;
	protected ItemSelectionSupport mSelectionSupport;

	protected ActionMode mActionMode;
	protected boolean mIsActionMode;
	protected boolean mIsActionModeRequired;

	protected SwipeRefreshLayout mSwipeRefreshLayout;

	public BaseRecyclerFragment() {
		super();
		mHelper = new FragmentHelper();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bridge.restoreInstanceState(this, savedInstanceState);
		if (mHelper == null)
			mHelper = new FragmentHelper(this);
		else
			mHelper.bindToFragment(this);
		mHelper.onCreate(savedInstanceState);
		if (mShouldRetainInstance)
			setRetainInstance(true);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.card_recycler_content, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		RecyclerView rv = getRecyclerView();
		rv.setLayoutManager(new GridLayoutManager(getActivity(), 1));
		rv.addItemDecoration(new SpacesItemDecoration(getAppCompatActivity().getResources().getDimensionPixelSize(R.dimen.recylcerview_content_margin)));
		mItemClickSupport = ItemClickSupport.addTo(rv);
		mItemClickSupport.setOnItemClickListener(this);
		mItemClickSupport.setOnItemLongClickListener(this);
		mSelectionSupport = ItemSelectionSupport.addTo(rv);
		super.onViewCreated(view, savedInstanceState);
		setFabEnabled(R.id.fab_reload, mEnableReload);
		setFabEnabled(R.id.fab_main, mHasFabMain);
	}

	protected void setFabEnabled(int id, boolean enabled) {
		FloatingActionButton fab = getAppCompatActivity().findViewById(id);
		if (fab == null)
			return;

		fab.setTag(R.id.fab_scrolling_view_behavior_enabled, enabled);
		if (enabled) {
			fab.show();
		} else {
			fab.hide();
			((MainActivity) getAppCompatActivity()).unregisterFab(id);
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		//noinspection ConstantConditions
		mSwipeRefreshLayout = getView().findViewById(R.id.ptr_layout);
		mSwipeRefreshLayout.setOnRefreshListener(this);
		mHelper.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onResume() {
		super.onResume();
		mHelper.onResume();
	}

	@Override
	public void onPause() {
		mHelper.onPause();
		super.onPause();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		mHelper.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
		Bridge.saveInstanceState(this, outState);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		if (!getMultiPaneHandler().isDrawerOpen())
			createOptionsMenu(menu, inflater);
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
	}

	@Override
	public MultiPaneHandler getMultiPaneHandler() {
		return mHelper.getMultiPaneHandler();
	}

	@Override
	public boolean hasHeader() {
		return false;
	}

	public String getBaseTitle() {
		return mHelper.getBaseTitle();
	}

	public void setBaseTitle(String baseTitle) {
		mHelper.setBaseTitle(baseTitle);
	}

	public String getCurrentTitle() {
		return mHelper.getCurrenTtitle();
	}

	public void setCurrentTitle(String currentTitle) {
		mHelper.setCurrentTitle(currentTitle);
	}

	public void initTitle(String title) {
		mHelper.setBaseTitle(title);
		mHelper.setCurrentTitle(title);
	}

	protected void finish() {
		finish(Statics.RESULT_NONE, null);
	}

	protected void finish(int resultCode) {
		finish(resultCode, null);
	}

	protected void finish(int resultCode, Intent data) {
		mHelper.finish(resultCode, data);
	}

	protected AppCompatActivity getAppCompatActivity() {
		return (AppCompatActivity) getActivity();
	}

	protected void showToast(String toastText) {
		Toast toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	protected void showToast(CharSequence toastText) {
		Toast toast = Toast.makeText(getAppCompatActivity(), toastText, Toast.LENGTH_LONG);
		toast.show();
	}

	public RecyclerView getRecyclerView() {
		//noinspection ConstantConditions
		return (RecyclerView) getView().findViewById(android.R.id.list);
	}

	protected void setEmptyText(CharSequence emptyText) {
		setEmptyText(emptyText, R.drawable.ic_warning_48dp);
	}

	protected void setEmptyText(CharSequence emptyText, int topDrawable) {
		//noinspection ConstantConditions
		View view = getView();
		if(view == null)
			return;
		TextView textView = view.findViewById(android.R.id.empty);
		if (textView == null)
			return;
		textView.setCompoundDrawablesWithIntrinsicBounds(0, topDrawable, 0, 0);
		if(emptyText == null) {
			textView.setText("");
			textView.setVisibility(View.GONE);
		} else {
			textView.setText(emptyText);
			textView.setVisibility(View.VISIBLE);
		}
	}

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, View view, int position, long id) {
		return false;
	}

	@Override
	public void onRefresh() {
		mSwipeRefreshLayout.setRefreshing(false);
	}

	@Override
	public void onDrawerClosed() {
	}

	@Override
	public void onDrawerOpened() {
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}

	protected void startActionMode() {
	}

	protected void endActionMode() {
		if (mActionMode != null)
			mActionMode.finish();
		mActionMode = null;
	}

	protected void registerFab(int id, int descriptionId, int backgroundResId, View.OnClickListener onClickListener) {
		registerFab(id, descriptionId, backgroundResId, onClickListener, false);
	}

	protected void registerFab(int id, int descriptionId, int backgroundResId, View.OnClickListener onClickListener, boolean topAligned) {
		FloatingActionButton fab = getAppCompatActivity().findViewById(id);
		if (fab == null)
			return;

		fab.setTag(R.id.fab_scrolling_view_behavior_enabled, true);
		fab.show();
		fab.setContentDescription(getString(descriptionId));
		fab.setImageResource(backgroundResId);
		fab.setOnClickListener(onClickListener);
		fab.setOnLongClickListener(v -> {
			Toast.makeText(getAppCompatActivity(), v.getContentDescription(), Toast.LENGTH_SHORT).show();
			return true;
		});
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
	}
}
