/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.TimerAdapter;
import net.reichholf.dreamdroid.enigma.Timer;
import net.reichholf.dreamdroid.enigma.TimerListLoadKt;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerChangeRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerCleanupRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.TimerDeleteRequestHandler;
import net.reichholf.dreamdroid.ui.services.TimerListItem;
import net.reichholf.dreamdroid.ui.services.TimerListMapper;
import net.reichholf.dreamdroid.ui.services.TimerListMapperKt;
import net.reichholf.dreamdroid.ui.services.TimerListState;
import net.reichholf.dreamdroid.ui.services.TimerListStateKt;
import net.reichholf.dreamdroid.widget.helper.ItemSelectionSupport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Activity to show a List of all existing timers of the target device.
 * Compose list of typed {@link Timer}; edit/delete still take ExtendedHashMap at the edge.
 * Load via coroutine + {@code EnigmaClient.getTimers()}.
 *
 * @author sreichholf
 */
public class TimerListFragment extends BaseHttpRecyclerFragment {
	private static final String KEY_TIMER = "timer_selected";

	@NonNull
	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

		// Called when the action mode is created; startActionMode() was called
		@Override
		public boolean onCreateActionMode(@NonNull ActionMode mode, Menu menu) {
			// Inflate a menu resource providing context menu items
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.timerlist_context, menu);
			mIsActionMode = true;
			mSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.SINGLE);
			return true;
		}

		// Called each time the action mode is shown. Always called after onCreateActionMode, but
		// may be called multiple times if the mode is invalidated.
		@Override
		public boolean onPrepareActionMode(ActionMode mode, @NonNull Menu menu) {
			MenuItem toggle = menu.findItem(R.id.menu_toggle_enabled);
			if (mTimer.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DISABLED).equals("0"))
				toggle.setTitle(R.string.disable);
			else
				toggle.setTitle(R.string.enable);
			return true;
		}

		// Called when the user selects a contextual menu item
		@Override
		public boolean onActionItemClicked(@NonNull ActionMode mode, @NonNull MenuItem item) {
			mode.finish(); // Action picked, so close the CAB
			return onItemSelected(item.getItemId());
		}

		// Called when the user exits the action mode
		@Override
		public void onDestroyActionMode(ActionMode mode) {

			mIsActionMode = false;
			if (mIsActionModeRequired)
				return;
			final RecyclerView rv = getRecyclerView();
			mSelectionSupport.setItemChecked(mSelectionSupport.getCheckedItemPosition(), false);
			getRecyclerView().post(() -> mSelectionSupport.setChoiceMode(ItemSelectionSupport.ChoiceMode.SINGLE));
		}
	};
	public ExtendedHashMap mTimer;
	@Nullable
	private ProgressDialog mProgress;
	protected int mCurrentPos;
	private TimerListState mListState;
	private final ArrayList<Timer> mTimers = new ArrayList<>();
	@Nullable
	private Job mLoadJob;
	/** Bumped on each new fetch / stop so stale load callbacks are ignored. */
	private int mTimerListGeneration = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		mCardListStyle = true;
		mEnableReload = true;
		mHasFabMain = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.timer));

		if (savedInstanceState != null) {
			@SuppressWarnings("deprecation")
			ExtendedHashMap timer = (ExtendedHashMap) savedInstanceState.getSerializable(KEY_TIMER);
			mTimer = timer;
		}

		mCurrentPos = -1;
		mIsActionMode = false;
		mReload = true;
		mListState = new TimerListState();
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		if (mTimer != null) {
			outState.putSerializable(KEY_TIMER, mTimer);
		}
		super.onSaveInstanceState(outState);
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.compose_swipe_list, container, false);
		registerFab(R.id.fab_main, R.string.new_timer, R.drawable.ic_action_fab_add, v -> onItemSelected(Statics.ITEM_NEW_TIMER));
		return view;
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setAdapter();
		ComposeView compose = view.findViewById(R.id.compose_list);
		TimerListStateKt.bindTimerListScreen(
				compose,
				mListState,
				item -> {
					onComposeClick(item, false);
					return kotlin.Unit.INSTANCE;
				},
				item -> {
					onComposeClick(item, true);
					return kotlin.Unit.INSTANCE;
				}
		);
	}

	@Override
	public void onDestroyView() {
		endActionMode();
		mTimerListGeneration++;
		cancelLoad(true);
		// viewLifecycleOwner cancels the Job with the view; re-fetch after recreate if empty.
		if (mTimers.isEmpty()) {
			mReload = true;
		}
		super.onDestroyView();
	}

	private void cancelLoad(boolean finishUi) {
		if (mLoadJob == null) {
			return;
		}
		mLoadJob.cancel(null);
		mLoadJob = null;
		if (finishUi) {
			finishLoadUi();
		}
	}

	private void finishLoadUi() {
		mHttpHelper.onLoadFinished();
	}

	protected void startActionMode() {
		mTimer = mMapList.get(mCurrentPos);
		mActionMode = getAppCompatActivity().startSupportActionMode(mActionModeCallback);
		mSelectionSupport.setItemChecked(mCurrentPos, true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.support.v4.app.Fragment#onActivityResult(int, int,
	 * android.content.Intent)
	 */
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == Statics.REQUEST_EDIT_TIMER) {
			if (resultCode == Activity.RESULT_OK) {
				if (getAppCompatActivity() != null) // we're somewhere active!
					// reload();
					Log.w(DreamDroid.LOG_TAG, "TIMER SAVED!");
			}
		}
	}

	@Override
	public void createOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
		super.createOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.timerlist, menu);
	}

	@Override
	public boolean onItemSelected(int id) {
		switch (id) {
			case (Statics.ITEM_NEW_TIMER):
				mTimer = net.reichholf.dreamdroid.helpers.enigma2.Timer.getInitialTimer();
				editTimer(mTimer, true);
				return true;
			case (Statics.ITEM_CLEANUP):
				cleanupTimerList();
				return true;
			case Statics.ITEM_TOGGLE_ENABLED:
				toggleTimerEnabled(mTimer);
				return true;
			case Statics.ITEM_DELETE:
				deleteTimerConfirm();
				return true;
			default:
				return super.onItemSelected(id);
		}
	}

	/**
	 * Open a <code>TimerEditActivity</code> for timer editing
	 *
	 * @param timer The timer to be edited
	 */
	private void editTimer(ExtendedHashMap timer, boolean create) {
		net.reichholf.dreamdroid.helpers.enigma2.Timer.edit(getMultiPaneHandler(), timer, this, create);
	}

	/**
	 * Initializes the <code>SimpleTextAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new TimerAdapter(getAppCompatActivity(), mMapList);
		getRecyclerView().setAdapter(mAdapter);
	}

	@Override
	protected void reload() {
		mReload = false;
		if (mTimers.isEmpty())
			setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
		else
			setEmptyText(null);
		loadTimers();
	}

	private void loadTimers() {
		if (!isAdded() || getView() == null) {
			return;
		}
		mHttpHelper.onLoadStarted();
		if (!"".equals(getBaseTitle().trim())) {
			setCurrentTitle(getString(R.string.loading));
		}
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}
		final int generation = ++mTimerListGeneration;
		cancelLoad(false);
		mLoadJob = TimerListLoadKt.launchTimerListLoad(this, (success, timers, errorText) -> {
			if (generation != mTimerListGeneration) {
				return Unit.INSTANCE;
			}
			handleTimerListReady(success, timers, errorText);
			return Unit.INSTANCE;
		});
	}

	private void handleTimerListReady(boolean success, @NonNull List<Timer> timers, @Nullable String errorText) {
		if (!isAdded()) {
			return;
		}
		finishLoadUi();
		mTimers.clear();
		mMapList.clear();
		mListState.replaceAll(Collections.emptyList());
		if (mAdapter != null) {
			mAdapter.notifyDataSetChanged();
		}
		if (!success) {
			setEmptyText(errorText);
			return;
		}
		setEmptyText(null);
		setCurrentTitle(getLoadFinishedTitle());
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getCurrentTitle());
		}

		if (timers.isEmpty()) {
			setEmptyText(getText(R.string.no_list_item));
		} else {
			mTimers.addAll(timers);
			for (Timer timer : timers) {
				mMapList.add(TimerListMapper.toExtendedHashMap(timer));
			}
			if (getAppCompatActivity() != null) {
				mListState.replaceAll(TimerListMapperKt.timerListItemsFrom(getAppCompatActivity(), mTimers));
			}
			if (mAdapter != null) {
				mAdapter.notifyDataSetChanged();
			}
		}
	}

	private void onComposeClick(@NonNull TimerListItem item, boolean isLong) {
		int position = item.getIndex();
		if (isLong) {
			onItemLongClick(getRecyclerView(), getView(), position, position);
		} else {
			onItemClick(getRecyclerView(), getView(), position, position);
		}
	}

	/**
	 * Confirmation dialog before timer deletion
	 */
	private void deleteTimerConfirm() {
		PositiveNegativeDialog dia = PositiveNegativeDialog.newInstance(mTimer.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_NAME),
				R.string.delete_confirm, android.R.string.yes, Statics.ACTION_DELETE_CONFIRMED, android.R.string.no,
				Statics.ACTION_NONE);

		getMultiPaneHandler().showDialogFragment(dia, "dialog_delete_timer_confirm");
	}

	/**
	 * Delete a timer by creating an <code>DeleteTimerTask</code>
	 *
	 * @param timer The Timer to delete as <code>ExtendedHashMap</code>
	 */
	private void deleteTimer(@NonNull ExtendedHashMap timer) {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}
		ArrayList<NameValuePair> params = net.reichholf.dreamdroid.helpers.enigma2.Timer.getDeleteParams(timer);
		mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.deleting), true);
		execSimpleResultTask(new TimerDeleteRequestHandler(), params);
	}

	private void toggleTimerEnabled(@NonNull ExtendedHashMap timer) {
		ExtendedHashMap timerNew = timer.clone();

		if (timerNew.getString(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DISABLED).equals("1"))
			timerNew.put(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DISABLED, "0");
		else
			timerNew.put(net.reichholf.dreamdroid.helpers.enigma2.Timer.KEY_DISABLED, "1");

		ArrayList<NameValuePair> params = net.reichholf.dreamdroid.helpers.enigma2.Timer.getSaveParams(timerNew, timer);
		mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.saving), true);
		execSimpleResultTask(new TimerChangeRequestHandler(), params);
	}

	/**
	 * CleanUp timer list by creating an <code>CleanupTimerListTask</code>
	 */
	private void cleanupTimerList() {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.cleaning_timerlist), true);
		execSimpleResultTask(new TimerCleanupRequestHandler(), new ArrayList<>());
	}

	@Override
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		super.onSimpleResult(success, result);

		reload();
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		switch (action) {
			case Statics.ACTION_EDIT:
				editTimer(mTimer, false);
				break;
			case Statics.ACTION_DELETE:
				deleteTimerConfirm();
				break;
			case Statics.ACTION_DELETE_CONFIRMED:
				deleteTimer(mTimer);
				break;
			default:
				break;
		}
	}

	@Override
	public void onItemClick(RecyclerView parent, View view, int position, long id) {
		mTimer = mMapList.get(position);
		if (mIsActionMode) {
			mSelectionSupport.setItemChecked(position, true);
			return;
		}
		editTimer(mTimer, false);
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, View view, int position, long id) {
		mTimer = mMapList.get(position);
		getAppCompatActivity().startSupportActionMode(mActionModeCallback);
		mSelectionSupport.setItemChecked(position, true);
		return true;
	}
}
