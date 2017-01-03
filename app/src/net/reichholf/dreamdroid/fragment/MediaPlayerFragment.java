/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.support.v4.widget.SlidingPaneLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.MediaListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SimpleChoiceDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.Python;
import net.reichholf.dreamdroid.helpers.Statics;
import net.reichholf.dreamdroid.helpers.enigma2.Mediaplayer;
import net.reichholf.dreamdroid.helpers.enigma2.SimpleResult;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.AbstractSimpleRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MediaplayerCommandRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MediaplayerCurrentRequestHandler;
import net.reichholf.dreamdroid.helpers.enigma2.requesthandler.MediaplayerListRequestHandler;
import net.reichholf.dreamdroid.loader.AsyncListLoader;
import net.reichholf.dreamdroid.loader.LoaderResult;

import java.util.ArrayList;

/**
 * Activity to show a list of all existing media files of the target device
 *
 * @author asc
 */
public class MediaPlayerFragment extends AbstractHttpListFragment implements ActionDialog.DialogActionListener {
	public static final String STATE_MEDIA_INDEX = "media_index";
	public static int LOADER_PLAYLIST_ID = 1;
	public static String PLAYLIST_AS_ROOT = "playlist";

	private ExtendedHashMap mMedia;
	private int mMediaIndex;
	private SimpleChoiceDialog mChoice;
	private ArrayList<NameValuePair> mFileListParams;
	static int PLAY_MODE = 0;
	static int STOP_MODE = 1;
	int mMode = STOP_MODE;
	private ExtendedHashMap mMediaInfo;

	private MediaListAdapter mPlaylistAdapter;
	private ArrayList<ExtendedHashMap> mPlaylist;

	private GetCurrentMediaInfoTask mGetCurrentMediaInfoTask;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.dual_list_media_view, null, false);
		setAdapter(v);

		// only if detail view is available the application should have
		// listeners for the buttons
		if (isDetailViewAvailable(v)) {
			ImageButton togglePlaylistButton = (ImageButton) v.findViewById(R.id.toggle_playlist);
			ListView playList = (ListView) v.findViewById(R.id.playlist);
			playList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> adapterView, View v, int position, long id) {
					ExtendedHashMap item = mPlaylist.get(position);
					playFile(item.getString(Mediaplayer.KEY_SERVICE_REFERENCE), PLAYLIST_AS_ROOT);
				}
			});

			playList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
				@Override
				public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
					ExtendedHashMap item = mPlaylist.get(position);
					deleteFromPlaylist(item.getString(Mediaplayer.KEY_SERVICE_REFERENCE));
					return true;
				}
			});

			togglePlaylistButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					ListView playList = (ListView) getView().findViewById(R.id.playlist);
					ImageView cover = (ImageView) getView().findViewById(R.id.cover);
					int vis = playList.getVisibility();
					playList.setVisibility(cover.getVisibility());
					cover.setVisibility(vis);
				}
			});

			ImageButton playButton = (ImageButton) v.findViewById(R.id.imageButtonPlay);
			playButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					play();
				}
			});

			ImageButton stopButton = (ImageButton) v.findViewById(R.id.imageButtonStop);
			stopButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					stop();
				}
			});

			ImageButton previousButton = (ImageButton) v.findViewById(R.id.imageButtonPrevious);
			previousButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					previous();
				}
			});

			ImageButton nextbutton = (ImageButton) v.findViewById(R.id.imageButtonNext);
			nextbutton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					next();
				}
			});
		}

		SlidingPaneLayout spl = (SlidingPaneLayout) v.findViewById(R.id.sliding_pane);
		if (spl != null) {
			spl.setPanelSlideListener(new SlidingPaneLayout.PanelSlideListener() {
				@Override
				public void onPanelSlide(View view, float v) {
				}

				@Override
				public void onPanelOpened(View view) {
					getListView().setEnabled(true);
					getAppCompatActivity().supportInvalidateOptionsMenu();
				}

				@Override
				public void onPanelClosed(View view) {
					getListView().setEnabled(false);
					getAppCompatActivity().supportInvalidateOptionsMenu();
				}
			});
			spl.openPane();
		}

		reloadPlaylist();
		reload();

		return v;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initTitle(getString(R.string.mediaplayer));

		mPlaylist = new ArrayList<>();
		mMediaInfo = new ExtendedHashMap();
		if (savedInstanceState != null) {
			mMediaIndex = savedInstanceState.getInt(STATE_MEDIA_INDEX, -1);
			if (mMediaIndex < 0)
				return;
			mMedia = mMapList.get(mMediaIndex);
			String isDirectory = (String) mMedia.get(Mediaplayer.KEY_IS_DIRECTORY);
			// only navigate into a directory
			if (isDirectory.equals("True")) {
				String mediaPath = (String) mMedia.get(Mediaplayer.KEY_SERVICE_REFERENCE);
				setArgs("path", mediaPath);
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putInt(STATE_MEDIA_INDEX, mMediaIndex);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onPause() {
		if (mGetCurrentMediaInfoTask != null) {
			if (mGetCurrentMediaInfoTask.getStatus() == GetCurrentMediaInfoTask.Status.RUNNING)
				mGetCurrentMediaInfoTask.cancel(true);
			mGetCurrentMediaInfoTask = null;
		}
		super.onPause();
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mMediaIndex = position;
		mMedia = mMapList.get(mMediaIndex);

		String isDirectory = (String) mMedia.get(Mediaplayer.KEY_IS_DIRECTORY);

		// only navigate into a directory
		if (Python.TRUE.equals(isDirectory)) {
			String mediaPath = (String) mMedia.get(Mediaplayer.KEY_SERVICE_REFERENCE);
			setArgs("path", mediaPath);
			reload();
		} else {
			CharSequence[] actions = {getText(R.string.play), getText(R.string.add_to_playlist)};
			int[] actionIds = {Statics.ACTION_PLAY_MEDIA, Statics.ACTION_ADD_TO_PLAYLIST};

			mChoice = SimpleChoiceDialog.newInstance(getString(R.string.pick_action), actions, actionIds);
			getMultiPaneHandler().showDialogFragment(mChoice, "dialog_play_media");
		}
	}

	@Override
	public ArrayList<NameValuePair> getHttpParams(int loader) {
		if(loader == LOADER_PLAYLIST_ID) {
			ArrayList<NameValuePair> params = new ArrayList<>();
			params.add(new NameValuePair("path", PLAYLIST_AS_ROOT));
			return params;
		} else { //LOADER_DEFAULT_ID
			if(mFileListParams == null)
				mFileListParams = new ArrayList<>();
			return mFileListParams;
		}
	}

	public void reloadPlaylist() {
		reload(LOADER_PLAYLIST_ID);
	}

	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
							   LoaderResult<ArrayList<ExtendedHashMap>> result) {

		if (loader.getId() == LOADER_PLAYLIST_ID) {
			setCurrentTitle(getLoadFinishedTitle());
			mPlaylist.clear();
			if (result.isError()) {
				showToast(result.getErrorText());
				return;
			}
			mPlaylist.addAll(result.getResult());
			mPlaylistAdapter.notifyDataSetChanged();
			return;
		}

		mMapList.clear();
		if (result.isError()) {
			setEmptyText(result.getErrorText());
			return;
		}
		ArrayList<ExtendedHashMap> list = result.getResult();

		if (list.size() == 0)
			setEmptyText(getText(R.string.no_list_item));
		else {
			// get first media item
			ExtendedHashMap media = list.get(0);

			// save current media object
			mMedia = media;
			if (mMode == PLAY_MODE) {
				// add media info to detail view
				setMediaInfo(mMediaInfo);

				// add image to detail view
				ImageView imageView = (ImageView) getView().findViewById(R.id.cover);

				ArrayList<NameValuePair> params = new ArrayList<>();
				params.add(new NameValuePair("file", "/tmp/.id3coverart"));
				String imageUrl = getHttpClient().buildUrl("/file?", params);
				// String imageUrl =
				// "http://192.168.2.100/file?file=/tmp/.id3coverart";
				Picasso.with(getContext()).load(imageUrl).fit().centerInside().into(imageView);
			}

			// check for changes in options menu
			getAppCompatActivity().supportInvalidateOptionsMenu();

			String root = media.getString(Mediaplayer.KEY_ROOT);
			String path = "";

			// create current title and remove first item from result list if
			// not a root item
			if (!Python.NONE.equals(root)) {
				path = " - " + root;
				list.remove(0);
			}

			setCurrentTitle(getLoadFinishedTitle() + path);
			mMapList.addAll(list);
		}
		getAppCompatActivity().setTitle(getCurrentTitle());
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void createOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.mediaplayer, menu);
	}

	private SlidingPaneLayout getSlidingPaneLayout() {
		return (SlidingPaneLayout) getView().findViewById(R.id.sliding_pane);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		if(getMultiPaneHandler().isDrawerOpen())
			return;
		if (mMedia != null) {
			String rootPath = (String) mMedia.get(Mediaplayer.KEY_ROOT);
			MenuItem homeMenuItem = menu.findItem(Statics.ITEM_MEDIA_HOME);
			MenuItem backMenuItem = menu.findItem(Statics.ITEM_MEDIA_BACK);

			boolean isPaneOpen = true;
			SlidingPaneLayout spl = getSlidingPaneLayout();
			if (spl != null)
				isPaneOpen = spl.isOpen();

			if (!isPaneOpen || rootPath.equals("None")) {
				homeMenuItem.setVisible(false);
				backMenuItem.setVisible(false);
			} else {
				homeMenuItem.setVisible(true);
				backMenuItem.setVisible(true);
			}
		}
	}

	@Override
	public boolean onItemSelected(int id) {
		if (mMedia == null) //TODO why does this even happen?
			return super.onItemSelected(id);

		switch (id) {
			case (Statics.ITEM_MEDIA_HOME):
				if (mFileListParams != null) {
					mFileListParams.clear();
				}
				// loads media files without path parameter
				reload();
				return true;

			case (Statics.ITEM_MEDIA_BACK):
				String mediaPath = (String) mMedia.get(Mediaplayer.KEY_SERVICE_REFERENCE);
				// if root path then clear args
				if (Python.NONE.equals(mediaPath)) {
					if (mFileListParams != null) {
						mFileListParams.clear();
					}
				} else {
					setArgs("path", mediaPath);
				}

				reload();
				return true;

			case (Statics.ITEM_MEDIA_CLOSE):
				closePlayer();
				return true;

			default:
				return super.onItemSelected(id);
		}
	}

	/**
	 * @author Get current media information async
	 */
	private class GetCurrentMediaInfoTask extends AsyncTask<String, String, Boolean> {

		@Override
		protected Boolean doInBackground(String... params) {
			mMediaInfo = new ExtendedHashMap();

			if (isCancelled())
				return false;

			publishProgress(getBaseTitle() + " - " + getString(R.string.fetching_data));

			String xml;
			AbstractSimpleRequestHandler handler = new MediaplayerCurrentRequestHandler();

			xml = handler.get(getHttpClient());

			if (xml != null && !isCancelled()) {
				publishProgress(getBaseTitle() + " - " + getString(R.string.parsing));
				boolean result = false;
				result = handler.parse(xml, mMediaInfo);
				return result;

			}
			return false;
		}

		@Override
		protected void onProgressUpdate(String... progress) {
			if (!isCancelled())
				updateProgress(progress[0]);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if(isCancelled())
				return;
			String title = getCurrentTitle();

			if (result) {
				setMediaInfo(mMediaInfo);
			} else {
				title = getString(R.string.get_content_error);

				if (getHttpClient().hasError()) {
					showToast(getString(R.string.get_content_error) + "\n" + getHttpClient().getErrorText(getContext()));
				}
			}

			mHttpHelper.finishProgress(title);

		}
	}

	public void clearMediaInfo() {
		TextView artist = (TextView) getView().findViewById(R.id.artist);
		// TextView album = (TextView) getView().findViewById(R.id.album);
		// TextView year = (TextView) getView().findViewById(R.id.year);
		// TextView category = (TextView) getView().findViewById(R.id.category);
		TextView title = (TextView) getView().findViewById(R.id.title);

		artist.setText("-");
		// album.setText("-");
		// year.setText("");
		// category.setText("");
		title.setText("-");
	}

	public void setMediaInfo(ExtendedHashMap map) {
		TextView artist = (TextView) getView().findViewById(R.id.artist);
		TextView title = (TextView) getView().findViewById(R.id.title);
		// TextView album = (TextView) getView().findViewById(R.id.album);
		// TextView year = (TextView) getView().findViewById(R.id.year);
		// TextView category = (TextView) getView().findViewById(R.id.category);

		artist.setText(map.getString(Mediaplayer.KEY_ARTIST));
		title.setText(map.getString(Mediaplayer.KEY_TITLE));
		// album.setText(map.getString(Mediaplayer.KEY_ALBUM));
		// year.setText(map.getString(Mediaplayer.KEY_YEAR));
		// category.setText(map.getString(Mediaplayer.KEY_GENRE));
	}

	/**
	 * @param progress
	 */
	protected void updateProgress(String progress) {
		mHttpHelper.updateProgress(progress);
	}

	// remove from playlist
	// /web/mediaplayerremove?file=/media/hdd/andreas/Music/A - Z/A/ABBA - Bang
	// A Boomerang 1975.mp3
	// change to filelist view /web/mediaplayerlist?path=filesystems
	// change to playlist view /web/mediaplayerlist?path=playlist
	// added to playlist /web/mediaplayerplay?file=/media/hdd/andreas/Music/A -
	// Z/A/ABBA - When I Kissed The Teacher 1976.mp3
	// save playlist /web/mediaplayerwrite?filename=/media/hdd/andreas/Music/A -
	// Z/A/playlist1

	/**
	 * add to playlist
	 *
	 * @param filePath
	 */
	private void addToPlaylist(String filePath) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_FILE, filePath));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_ADD), params);
	}

	/**
	 * delete from playlist
	 *
	 * @param filePath
	 */
	private void deleteFromPlaylist(String filePath) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_FILE, filePath));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_REMOVE), params);
	}

	/**
	 * play media file
	 *
	 * @param filePath
	 */
	private void playFile(String filePath, String root) {
		mMode = PLAY_MODE;

		// invalidate options menu
		getAppCompatActivity().supportInvalidateOptionsMenu();

		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_FILE, filePath));
		if (root != null)
			params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_ROOT, root));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_PLAY), params);
	}

	// play current media item
	private void play() {
		mMode = PLAY_MODE;

		// invalidate options menu
		getAppCompatActivity().supportInvalidateOptionsMenu();

		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_PLAY));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);
	}

	// stop playing media item
	private void stop() {
		mMode = STOP_MODE;

		// invalidate options menu
		getAppCompatActivity().supportInvalidateOptionsMenu();

		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_STOP));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);

		clearMediaInfo();
		ImageView imageView = (ImageView) getView().findViewById(R.id.cover);
		imageView.setImageResource(R.drawable.no_cover_art);
	}

	// play previous media item
	private void previous() {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_PREVIOUS));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);
	}

	// play next media item
	private void next() {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_NEXT));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);
	}

	private void clearPlaylist() {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_CLEAR));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);
	}

	// close Mediaplayer
	private void closePlayer() {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_EXIT));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);
	}

	private void setArgs(String name, String value) {
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(name, value));
		mFileListParams = params;
	}

	/**
	 * Initializes the <code>MediaListAdapter</code>
	 */
	private void setAdapter(View v) {
		mAdapter = new MediaListAdapter(getAppCompatActivity(), mMapList);
		setListAdapter(mAdapter);

		mPlaylistAdapter = new MediaListAdapter(getAppCompatActivity(), mPlaylist);
		ListView playlistView = (ListView) v.findViewById(R.id.playlist);
		playlistView.setAdapter(mPlaylistAdapter);
	}

	/**
	 * Check if DetailView is available for current device
	 */
	private boolean isDetailViewAvailable(View v) {
		LinearLayout detailLayout = (LinearLayout) v.findViewById(R.id.detailView);

		return detailLayout != null;
	}

	private String getCommandFromKeyState(String keyStateText) {
		String command = "";

		if (keyStateText.contains(MediaplayerCommandRequestHandler.CMD_NEXT)) {
			command = MediaplayerCommandRequestHandler.CMD_NEXT;
		}

		if (keyStateText.contains(MediaplayerCommandRequestHandler.CMD_PLAY)) {
			command = MediaplayerCommandRequestHandler.CMD_PLAY;
		}

		if (keyStateText.contains(MediaplayerCommandRequestHandler.CMD_PREVIOUS)) {
			command = MediaplayerCommandRequestHandler.CMD_PREVIOUS;
		}

		if (keyStateText.startsWith("Playback")) {
			command = MediaplayerCommandRequestHandler.CMD_PLAY;
		}

		if (keyStateText.contains(MediaplayerCommandRequestHandler.CMD_EXIT)) {
			command = MediaplayerCommandRequestHandler.CMD_EXIT;
		}

		return command;
	}

	@Override
	public void onSimpleResult(boolean success, ExtendedHashMap result) {
		super.onSimpleResult(success, result);
		if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {
			String command = getCommandFromKeyState(result.getString(SimpleResult.KEY_STATE_TEXT));
			if (command.equals(MediaplayerCommandRequestHandler.CMD_NEXT)
					|| command.equals(MediaplayerCommandRequestHandler.CMD_PLAY)
					|| command.equals(MediaplayerCommandRequestHandler.CMD_PREVIOUS)) {
				// add image to detail view
				ImageView imageView = (ImageView) getView().findViewById(R.id.cover);

				ArrayList<NameValuePair> params = new ArrayList<>();
				params.add(new NameValuePair("file", "/tmp/.id3coverart"));

				String imageUrl = getHttpClient().buildUrl("/file?", params);
				Picasso.with(getContext()).load(imageUrl).fit().centerInside().into(imageView);

				getCurrentMediaInfo();
			}
			if(command.equals(MediaplayerCommandRequestHandler.CMD_EXIT))
				return;
			reload();
			reloadPlaylist();
		}
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		return new AsyncListLoader(getAppCompatActivity(), new MediaplayerListRequestHandler(), false, args);
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {
		String filePath = mMedia.getString(Mediaplayer.KEY_SERVICE_REFERENCE);

		switch (action) {
			case Statics.ACTION_ADD_TO_PLAYLIST:
				addToPlaylist(filePath);
				break;

			case Statics.ACTION_DELETE_FROM_PLAYLIST:
				deleteFromPlaylist(filePath);
				break;

			default:
				break;
		}
	}

	private void getCurrentMediaInfo() {
		// add media info to detail view
		if (mGetCurrentMediaInfoTask != null) {
			if (mGetCurrentMediaInfoTask.getStatus() == GetCurrentMediaInfoTask.Status.RUNNING)
				mGetCurrentMediaInfoTask.cancel(true);
			mGetCurrentMediaInfoTask = null;
		}
		mGetCurrentMediaInfoTask = new GetCurrentMediaInfoTask();
		mGetCurrentMediaInfoTask.execute();
	}

}
