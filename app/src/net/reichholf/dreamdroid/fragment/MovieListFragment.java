/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.compose.ui.platform.ComposeView;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;

import net.reichholf.dreamdroid.DreamDroid;
import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.recyclerview.SimpleTextAdapter;
import net.reichholf.dreamdroid.enigma.Movie;
import net.reichholf.dreamdroid.enigma.MovieListLoadKt;
import net.reichholf.dreamdroid.fragment.abs.BaseHttpRecyclerFragment;
import net.reichholf.dreamdroid.fragment.dialogs.MovieDetailBottomSheet;
import net.reichholf.dreamdroid.fragment.dialogs.MultiChoiceDialog;
import net.reichholf.dreamdroid.fragment.dialogs.PositiveNegativeDialog;
import net.reichholf.dreamdroid.fragment.helper.HttpFragmentHelper;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.Tag;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieDeleteRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MovieListRequestHandler;
import net.reichholf.dreamdroid.intents.IntentFactory;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;
import net.reichholf.dreamdroid.ui.services.MovieListItem;
import net.reichholf.dreamdroid.ui.services.MovieListMapperKt;
import net.reichholf.dreamdroid.ui.services.MovieListState;
import net.reichholf.dreamdroid.ui.services.MovieListStateKt;

import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;
import kotlinx.coroutines.Job;

/**
 * Allows browsing recorded movies. Supports filtering by tags and locations.
 * Compose Material 3 list of typed {@link Movie}; delete/stream still take ExtendedHashMap at the edge.
 * Load via coroutine + {@code EnigmaClient.getMovies()}.
 *
 * @author sreichholf
 */
public class MovieListFragment extends BaseHttpRecyclerFragment
		implements MultiChoiceDialog.MultiChoiceDialogListener {
	public static String ARGUMENT_LOCATION = "location";
	private boolean mTagsChanged;
	private boolean mReloadOnSimpleResult;

	@State public String mCurrentLocation;
	@State public ArrayList<String> mSelectedTags;
	@State public ArrayList<String> mOldTags;
	@State public ExtendedHashMap mMovie;
	private final ArrayList<Movie> mMovies = new ArrayList<>();
	private MovieListState mListState;
	@Nullable
	private Job mLoadJob;
	@Nullable
	private PendingMovieList mPendingMovieList;

	@Nullable
	private ProgressDialog mProgress;

	private static final class PendingMovieList {
		final boolean success;
		@NonNull final List<Movie> movies;
		@Nullable final String errorText;

		PendingMovieList(boolean success, @NonNull List<Movie> movies, @Nullable String errorText) {
			this.success = success;
			this.movies = movies;
			this.errorText = errorText;
		}
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		mCardListStyle = true;
		mEnableReload = true;
		//mHasFabMain = true;
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.movies));
		mCurrentLocation = null;
		if (savedInstanceState == null) {
			mSelectedTags = new ArrayList<>();
			mOldTags = new ArrayList<>();
		}
		setInitialLocation(savedInstanceState);
		mListState = new MovieListState();
	}

	protected void setInitialLocation(@Nullable Bundle savedInstanceState) {
		int locationArg = getArguments().getInt(ARGUMENT_LOCATION, -1);
		if (locationArg >= 0) {
			mCurrentLocation = DreamDroid.getLocations().get(
					getArguments().getInt(
							ARGUMENT_LOCATION,
							DreamDroid.getLocations().indexOf(mCurrentLocation)
					)
			);
		} else if (savedInstanceState == null) {
			if(mCurrentLocation != null && DreamDroid.getLocations().indexOf(mCurrentLocation) >= 0) {
				for (String location : DreamDroid.getLocations()) {
					mCurrentLocation = location;
					break;
				}
			}
		}
		mReload = true;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.compose_swipe_list, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		ComposeView compose = view.findViewById(R.id.compose_list);
		MovieListStateKt.bindMovieListScreen(
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
	public void onActivityCreated(Bundle savedInstanceState) {
		mAdapter = new SimpleTextAdapter(mMapList, R.layout.movie_list_item, new String[]{
				net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_TITLE,
				net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_SERVICE_NAME,
				net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_FILE_SIZE_READABLE,
				net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_TIME_READABLE,
				net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_LENGTH}, new int[]{R.id.movie_title, R.id.service_name, R.id.file_size, R.id.event_start,
				R.id.event_duration});
		getRecyclerView().setAdapter(mAdapter);
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onDestroyView() {
		cancelLoad(true);
		if (mMovies.isEmpty()) {
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
			mHttpHelper.onLoadFinished();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mPendingMovieList != null) {
			PendingMovieList pending = mPendingMovieList;
			mPendingMovieList = null;
			applyMovieList(pending.success, pending.movies, pending.errorText);
		}
	}

	@Override
	public void createOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
		super.createOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.locactions_and_tags, menu);
	}

	@Override
	protected boolean onItemSelected(int id) {
		switch (id) {
			case Statics.ITEM_TAGS:
				pickTags();
				return true;
			default:
				return super.onItemSelected(id);
		}
	}

	protected void pickTags() {
		CharSequence[] tags = new CharSequence[DreamDroid.getTags().size()];
		boolean[] selectedTags = new boolean[DreamDroid.getTags().size()];

		int tc = 0;
		for (String tag : DreamDroid.getTags()) {
			tags[tc] = tag;
			selectedTags[tc] = mSelectedTags.contains(tag);
			tc++;
		}

		mTagsChanged = false;
		mOldTags = new ArrayList<>();
		mOldTags.addAll(mSelectedTags);

		MultiChoiceDialog f = MultiChoiceDialog.newInstance(R.string.choose_tags, tags, selectedTags, R.string.ok,
				R.string.cancel);

		getMultiPaneHandler().showDialogFragment(f, "dialog_pick_tags");
	}

	@Override
	public void onItemClick(RecyclerView parent, @NonNull View view, int position, long id) {
		// Compose owns clicks.
	}

	@Override
	public boolean onItemLongClick(RecyclerView parent, @NonNull View view, int position, long id) {
		return true;
	}

	private void onMovieItemClick(@NonNull View view, int position, boolean isLong) {
		if (position < 0 || position >= mMovies.size()) {
			return;
		}
		Movie typed = mMovies.get(position);
		mMovie = MovieListMapperKt.movieToExtendedHashMap(typed);
		boolean isInsta = PreferenceManager.getDefaultSharedPreferences(getAppCompatActivity()).getBoolean(
				DreamDroid.PREFS_KEY_INSTANT_ZAP, false);
		if ((isInsta && !isLong) || (!isInsta && isLong)) {
			zapTo(typed.getReference());
		} else {
			showPopupMenu(view);
		}
	}

	public void showPopupMenu(@NonNull View v) {
		PopupMenu menu = new PopupMenu(getAppCompatActivity(), v);
		menu.getMenuInflater().inflate(R.menu.popup_movielist, menu.getMenu());
		menu.setOnMenuItemClickListener(menuItem -> onMovieAction(menuItem.getItemId()));
		menu.show();
	}

	/**
	 * Delete the selected movie
	 */
	private void deleteMovie() {
		if (mProgress != null) {
			if (mProgress.isShowing()) {
				mProgress.dismiss();
			}
		}

		mProgress = ProgressDialog.show(getAppCompatActivity(), "", getText(R.string.deleting), true);
		mReloadOnSimpleResult = true;
		execSimpleResultTask(new MovieDeleteRequestHandler(),
				net.reichholf.dreamdroid.helpers.enigma2.Movie.getDeleteParams(mMovie));
	}

	@Override
	public void onSimpleResult(boolean success, @NonNull ExtendedHashMap result) {
		if (mProgress != null) {
			mProgress.dismiss();
			mProgress = null;
		}
		super.onSimpleResult(success, result);

		if (mReloadOnSimpleResult) {
			if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
				reload();
				mReloadOnSimpleResult = false;
			}
		}
	}

	@NonNull
	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		if (mCurrentLocation != null) {
			params.add(new NameValuePair("dirname", mCurrentLocation));
		}

		if (mSelectedTags.size() > 0) {
			String tags = Tag.implodeTags(mSelectedTags);
			params.add(new NameValuePair("tag", tags));
		}

		return params;
	}

	@NonNull
	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		// Movies list no longer starts this loader. BaseHttpRecyclerFragment still requires LoaderCallbacks.
		return new AsyncListLoader(getAppCompatActivity(), new MovieListRequestHandler(), false, args);
	}

	@Override
	public void onLoadFinished(@NonNull Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   @NonNull LoaderResult<ArrayList<ExtendedHashMap>> result) {
		// Unused: rows come from EnigmaClient coroutines.
	}

	@Override
	protected void reload() {
		mReload = false;
		if (mMovies.isEmpty())
			setEmptyText(getText(R.string.loading), R.drawable.ic_loading_48dp);
		else
			setEmptyText(null);
		loadMovies();
	}

	private void loadMovies() {
		if (!isAdded() || getView() == null) {
			return;
		}
		mHttpHelper.onLoadStarted();
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(getString(R.string.loading));
		}
		cancelLoad(false);
		ArrayList<NameValuePair> params = getHttpParams(HttpFragmentHelper.LOADER_DEFAULT_ID);
		mLoadJob = MovieListLoadKt.launchMovieListLoad(this, params, (success, movies, errorText) -> {
			onMovieListReady(success, movies, errorText);
			return Unit.INSTANCE;
		});
	}

	private void onMovieListReady(boolean success, @NonNull List<Movie> movies, @Nullable String errorText) {
		if (!isAdded()) {
			return;
		}
		mHttpHelper.onLoadFinished();
		if (!isResumed()) {
			mPendingMovieList = new PendingMovieList(success, new ArrayList<>(movies), errorText);
			return;
		}
		applyMovieList(success, movies, errorText);
	}

	private void applyMovieList(boolean success, @NonNull List<Movie> movies, @Nullable String errorText) {
		mMovies.clear();
		mListState.replaceAll(java.util.Collections.emptyList());
		if (getAppCompatActivity() != null) {
			getAppCompatActivity().setTitle(mCurrentLocation != null ? mCurrentLocation : getBaseTitle());
		}
		if (!success) {
			setEmptyText(errorText);
			return;
		}
		setEmptyText(null);
		if (movies.isEmpty()) {
			setEmptyText(getText(R.string.no_list_item));
		} else {
			mMovies.addAll(movies);
			mListState.replaceAll(MovieListMapperKt.movieListItemsFromMovies(mMovies));
		}
	}

	private void onComposeClick(@NonNull MovieListItem item, boolean isLong) {
		View host = getView();
		if (host == null) {
			return;
		}
		onMovieItemClick(host, item.getIndex(), isLong);
	}

	public void setLocation(int index) {
		String selectedLoc = DreamDroid.getLocations().get(index);
		if (!selectedLoc.equals(mCurrentLocation)) {
			mCurrentLocation = selectedLoc;
			reload();
		}
		return;
	}

	public void onDialogAction(int action, Object details, String dialogTag) {
		onMovieAction(action);
	}

	public boolean onMovieAction(int action) {
		switch (action) {
			case R.id.menu_info: {
				String descriptionEx = mMovie.getString(
						net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_DESCRIPTION_EXTENDED);
				if (descriptionEx == null || descriptionEx.isEmpty()) {
					showToast(getString(R.string.no_epg_available));
					break;
				}
				Movie typed = findSelectedTypedMovie();
				if (typed != null) {
					getMultiPaneHandler().showDialogFragment(MovieDetailBottomSheet.newInstance(typed), "movie_detail_dialog");
				} else {
					getMultiPaneHandler().showDialogFragment(
							MovieDetailBottomSheet.newInstance(new net.reichholf.dreamdroid.helpers.enigma2.Movie(mMovie)),
							"movie_detail_dialog");
				}
				break;
			}

			case R.id.menu_zap:
				zapTo(mMovie.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_REFERENCE));
				break;

			case R.id.menu_delete:
				getMultiPaneHandler().showDialogFragment(
						PositiveNegativeDialog.newInstance(mMovie.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_TITLE), R.string.delete_confirm,
								android.R.string.yes, Statics.ACTION_DELETE_CONFIRMED, android.R.string.no,
								Statics.ACTION_NONE), "dialog_delete_movie_confirm");
				break;

			case Statics.ACTION_DELETE_CONFIRMED:
				deleteMovie();
				break;

			case R.id.menu_download:
				ArrayList<NameValuePair> params = new ArrayList<>();
				params.add(new NameValuePair("file", mMovie.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_FILE_NAME)));
				String url = getHttpClient().buildUrl(URIStore.FILE, params);

				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(intent);
				break;

			case R.id.menu_stream:
				try {
					startActivity(IntentFactory.getStreamFileIntent(getAppCompatActivity(),
							mMovie.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_REFERENCE),
							mMovie.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_FILE_NAME),
							mMovie.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_TITLE),
							mMovie));
				} catch (ActivityNotFoundException e) {
					showToast(getText(R.string.missing_stream_player));
				}
				break;
			default:
				return false;
		}
		return true;
	}

	@Nullable
	private Movie findSelectedTypedMovie() {
		if (mMovie == null) {
			return null;
		}
		String ref = mMovie.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_REFERENCE);
		String file = mMovie.getString(net.reichholf.dreamdroid.helpers.enigma2.Movie.KEY_FILE_NAME);
		for (Movie movie : mMovies) {
			if (movie.getReference().equals(ref) && movie.getFileName().equals(file)) {
				return movie;
			}
		}
		return null;
	}

	@Override
	public void onMultiChoiceDialogSelection(String dialogTag, DialogInterface dialog, @NonNull Integer[] selected) {
		ArrayList<String> tags = DreamDroid.getTags();
		ArrayList<String> selectedTags = new ArrayList<>();
		for (Integer which : selected) {
			selectedTags.add(tags.get(which));
		}
		mTagsChanged = !selectedTags.equals(mSelectedTags);
		mSelectedTags = selectedTags;
	}

	@Override
	public void onMultiChoiceDialogFinish(String dialogTag, int result) {
		if ("dialog_pick_tags".equals(dialogTag) && mTagsChanged)
			reload();
	}
}
