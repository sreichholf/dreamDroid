package net.reichholf.dreamdroid.fragment.abs;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.activities.abs.MultiPaneHandler;
import net.reichholf.dreamdroid.fragment.ActivityCallbackHandler;
import net.reichholf.dreamdroid.fragment.helper.DreamDroidFragmentHelper;
import net.reichholf.dreamdroid.fragment.interfaces.IBaseFragment;
import net.reichholf.dreamdroid.fragment.interfaces.IMutliPaneContent;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.widget.FloatingActionButton;
import net.reichholf.dreamdroid.widget.helper.ItemClickSupport;
import net.reichholf.dreamdroid.widget.helper.ItemSelectionSupport;


/**
 * Created by Stephan on 03.05.2015.
 */
public abstract class BaseRecyclerFragment extends Fragment implements ActivityCallbackHandler, IMutliPaneContent, IBaseFragment, ItemClickSupport.OnItemClickListener, ItemClickSupport.OnItemLongClickListener {

	private DreamDroidFragmentHelper mHelper;
	protected boolean mShouldRetainInstance = true;
	protected boolean mCardListStyle = false;

	protected ItemClickSupport mItemClickSupport;
	protected ItemSelectionSupport mSelectionSupport;

	public BaseRecyclerFragment() {
		super();
		mHelper = new DreamDroidFragmentHelper();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mHelper.onAttach(activity);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (mHelper == null)
			mHelper = new DreamDroidFragmentHelper(this);
		else
			mHelper.bindToFragment(this);
		mHelper.onCreate(savedInstanceState);
		if (mShouldRetainInstance)
			setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.card_recycler_content, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		RecyclerView rv = getRecyclerView();
		rv.setLayoutManager(new LinearLayoutManager(getActivity()));
		rv.addItemDecoration(new SpacesItemDecoration(15));
		mItemClickSupport = ItemClickSupport.addTo(rv);
		mItemClickSupport.setOnItemClickListener(this);
		mItemClickSupport.setOnItemLongClickListener(this);
		mSelectionSupport = ItemSelectionSupport.addTo(rv);
		super.onViewCreated(view, savedInstanceState);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
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
	public void onSaveInstanceState(Bundle outState) {
		mHelper.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
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
		return (RecyclerView) getView().findViewById(android.R.id.list);
	}

	protected void setEmptyText(String errorText) {
	}


	protected void setEmptyText(CharSequence text) {
	}


	protected void registerFab(int id, View view, View.OnClickListener onClickListener, RecyclerView recyclerView) {
		registerFab(id, view, onClickListener, recyclerView, false);
	}

	protected void registerFab(int id, View view, View.OnClickListener onClickListener, RecyclerView recyclerView, boolean topAligned) {
		FloatingActionButton fab = (FloatingActionButton) view.findViewById(id);
		if (fab == null)
			return;
		if (recyclerView != null)
			fab.attachToRecyclerView(recyclerView, topAligned);

		fab.setOnClickListener(onClickListener);
		fab.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				Toast.makeText(getAppCompatActivity(), v.getContentDescription(), Toast.LENGTH_SHORT).show();
				return true;
			}
		});
	}

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, View view, int position, long id) {
		return false;
	}

	public class SpacesItemDecoration extends RecyclerView.ItemDecoration {
		private int space;

		public SpacesItemDecoration(int space) {
			this.space = space;
		}

		@Override
		public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
			outRect.left = space;
			outRect.right = space;
			outRect.bottom = space;

			// Add top margin only for the first item to avoid double space between items
			if (parent.getChildPosition(view) == 0)
				outRect.top = space;
		}
	}
}
