/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.fragment;

import java.util.ArrayList;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.adapter.MediaListAdapter;
import net.reichholf.dreamdroid.fragment.abs.AbstractHttpListFragment;
import net.reichholf.dreamdroid.fragment.dialogs.ActionDialog;
import net.reichholf.dreamdroid.fragment.dialogs.SimpleChoiceDialog;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
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

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * Activity to show a list of all existing media files of the target device
 * 
 * @author asc
 * 
 */
public class MediaListFragment extends AbstractHttpListFragment implements ActionDialog.DialogActionListener {
	private ExtendedHashMap mMedia;
	private SimpleChoiceDialog mChoice;
	private Bundle args;
	protected ImageLoader imageLoader = ImageLoader.getInstance();
	static int PLAY_MODE = 0;
	static int STOP_MODE = 1;
	int mMode = STOP_MODE;
	private ExtendedHashMap mMediaInfo;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.dual_list_media_view, null, false);

		setAdapter();

		// only if detail view is available the application should have
		// listeners for the buttons
		if (isDetailViewAvailable(v)) {
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

		reload();

		return v;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getActionBarActivity().setProgressBarIndeterminateVisibility(false);

		initTitle(getString(R.string.mediaplayer));

		setHasOptionsMenu(true);

		if (savedInstanceState != null) {
			mMedia = (ExtendedHashMap) savedInstanceState.getParcelable("media_filesystem");

			String isDirectory = (String) mMedia.get(Mediaplayer.KEY_IS_DIRECTORY);

			// only navigate into a directory
			if (isDirectory.equals("True")) {
				String mediaPath = (String) mMedia.get(Mediaplayer.KEY_SERVICE_REFERENCE);

				setArgs("path", mediaPath);
			}
		}

	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable("media_filesystem", mMedia);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		mMedia = mMapList.get((int) id);

		String isDirectory = (String) mMedia.get(Mediaplayer.KEY_IS_DIRECTORY);
		String root = mMedia.getString(Mediaplayer.KEY_ROOT);

		// only navigate into a directory
		if (isDirectory.equals("True")) {
			String mediaPath = (String) mMedia.get(Mediaplayer.KEY_SERVICE_REFERENCE);

			setArgs("path", mediaPath);

			reload();
		} else if (root.equals("playlist")) {
			CharSequence[] actions = { getText(R.string.play), getText(R.string.delete_from_playlist) };
			int[] actionIds = { Statics.ACTION_PLAY_MEDIA, Statics.ACTION_DELETE_FROM_PLAYLIST };

			mChoice = SimpleChoiceDialog.newInstance(getString(R.string.pick_action), actions, actionIds);
			getMultiPaneHandler().showDialogFragment(mChoice, "dialog_play_media");
		} else {
			CharSequence[] actions = { getText(R.string.play), getText(R.string.add_to_playlist) };
			int[] actionIds = { Statics.ACTION_PLAY_MEDIA, Statics.ACTION_ADD_TO_PLAYLIST };

			mChoice = SimpleChoiceDialog.newInstance(getString(R.string.pick_action), actions, actionIds);
			getMultiPaneHandler().showDialogFragment(mChoice, "dialog_play_media");
		}
	}

	public Bundle getLoaderBundle() {
		return args;
	}

	public void onLoadFinished(Loader<LoaderResult<ArrayList<ExtendedHashMap>>> loader,
			LoaderResult<ArrayList<ExtendedHashMap>> result) {

		getActionBarActivity().setProgressBarIndeterminateVisibility(false);

		mMapList.clear();
		if (result.isError()) {
			setEmptyText(result.getErrorText());
			return;
		}

		ArrayList<ExtendedHashMap> list = result.getResult();

		if (list.size() == 0)
			// TODO add to dual_media_list_view.xml
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

				ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
				params.add(new BasicNameValuePair("file", "/tmp/.id3coverart"));

				String imageUrl = getHttpClient().buildUrl("/file?", params);

				// String imageUrl =
				// "http://192.168.2.100/file?file=/tmp/.id3coverart";
				imageLoader.displayImage(imageUrl, imageView);
			}

			// check for changes in options menu
			getActionBarActivity().invalidateOptionsMenu();

			String root = media.getString(Mediaplayer.KEY_ROOT);
			String path = "";

			// create Playlist as title
			if (root.equals("playlist")) {
				path = " - " + getString(R.string.playlist);
			}
			// create current title and remove first item from result list if
			// not a root item
			else if (!root.equals("None")) {
				path = " - " + root;
				list.remove(0);
			}

			setCurrentTitle(getLoadFinishedTitle() + path);
			getActionBarActivity().setTitle(getCurrentTitle());

			mMapList.addAll(list);
		}

		mAdapter.notifyDataSetChanged();
	}

	// @Override
	// public void setEmptyText(CharSequence text) {
	// showToast(text);
	// }

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.mediaplayer, menu);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {

		if (mMedia != null) {

			String rootPath = (String) mMedia.get(Mediaplayer.KEY_ROOT);
			MenuItem homeMenuItem = menu.findItem(Statics.ITEM_MEDIA_HOME);
			MenuItem backMenuItem = menu.findItem(Statics.ITEM_MEDIA_BACK);
			MenuItem filebrowserMenuItem = menu.findItem(Statics.ITEM_MEDIA_FILEBROWSER);
			MenuItem playlistMenuItem = menu.findItem(Statics.ITEM_MEDIA_PLAYLIST);

			MenuItem playMenuItem = menu.findItem(Statics.ITEM_MEDIA_PLAY);
			MenuItem stopMenuItem = menu.findItem(Statics.ITEM_MEDIA_STOP);

			MenuItem nextMenuItem = menu.findItem(Statics.ITEM_MEDIA_NEXT);
			MenuItem previousMenuItem = menu.findItem(Statics.ITEM_MEDIA_PREVIOUS);

			if (rootPath.equals("None")) {
				homeMenuItem.setEnabled(false);
				backMenuItem.setEnabled(false);
				playlistMenuItem.setEnabled(true);
				filebrowserMenuItem.setEnabled(false);
			} else if (rootPath.equals("playlist")) {
				filebrowserMenuItem.setEnabled(true);
				playlistMenuItem.setEnabled(false);
			} else {
				playlistMenuItem.setEnabled(true);
				homeMenuItem.setEnabled(true);
				backMenuItem.setEnabled(true);
				filebrowserMenuItem.setEnabled(false);
			}

			if (mMode == PLAY_MODE) {
				playMenuItem.setEnabled(false);
				stopMenuItem.setEnabled(true);
				nextMenuItem.setEnabled(true);
				previousMenuItem.setEnabled(true);
			} else {
				playMenuItem.setEnabled(true);
				stopMenuItem.setEnabled(false);
				nextMenuItem.setEnabled(false);
				previousMenuItem.setEnabled(false);
			}

		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case (Statics.ITEM_MEDIA_PLAYLIST):
			setArgs("path", "playlist");

			reload();

			return true;

		case (Statics.ITEM_MEDIA_FILEBROWSER):
			setArgs("path", "filesystems");

			reload();

			return true;

		case (Statics.ITEM_MEDIA_HOME):
			if (args != null) {
				args.clear();
			}

			// loads media files without path parameter
			reload();

			return true;

		case (Statics.ITEM_MEDIA_BACK):

			String mediaPath = (String) mMedia.get(Mediaplayer.KEY_SERVICE_REFERENCE);

			// if root path then clear args
			if (mediaPath.equals("None")) {
				if (args != null) {
					args.clear();
				}
			} else {
				setArgs("path", mediaPath);
			}

			reload();

			return true;

		case (Statics.ITEM_MEDIA_CLOSE):
			exit();
			return true;

		case (Statics.ITEM_MEDIA_PREVIOUS):
			previous();
			return true;

		case (Statics.ITEM_MEDIA_NEXT):
			next();
			return true;

		case (Statics.ITEM_MEDIA_STOP):
			stop();
			return true;

		case (Statics.ITEM_MEDIA_PLAY):
			play();
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * @author Get current media information async
	 */
	private class GetCurrentMediaInfoTask extends AsyncTask<String, String, Boolean> {
		// protected ExtendedHashMap mMediaInfo;

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

			String title = getCurrentTitle();

			if (result) {
				setMediaInfo(mMediaInfo);
			} else {
				title = getString(R.string.get_content_error);

				if (getHttpClient().hasError()) {
					showToast(getString(R.string.get_content_error) + "\n" + getHttpClient().getErrorText());
				}
			}

			mHttpHelper.finishProgress(title);

		}
	}

	public void deleteMediaInfo() {
		TextView artist = (TextView) getView().findViewById(R.id.artist);
		TextView album = (TextView) getView().findViewById(R.id.album);
		TextView year = (TextView) getView().findViewById(R.id.year);
		TextView category = (TextView) getView().findViewById(R.id.category);
		TextView title = (TextView) getView().findViewById(R.id.title);

		artist.setText("");
		album.setText("");
		year.setText("");
		category.setText("");
		title.setText("");
	}

	public void setMediaInfo(ExtendedHashMap map) {
		TextView artist = (TextView) getView().findViewById(R.id.artist);
		TextView album = (TextView) getView().findViewById(R.id.album);
		TextView year = (TextView) getView().findViewById(R.id.year);
		TextView category = (TextView) getView().findViewById(R.id.category);
		TextView title = (TextView) getView().findViewById(R.id.title);

		artist.setText(map.getString(Mediaplayer.KEY_ARTIST));
		album.setText(map.getString(Mediaplayer.KEY_ALBUM));
		year.setText(map.getString(Mediaplayer.KEY_YEAR));
		category.setText(map.getString(Mediaplayer.KEY_GENRE));
		title.setText(map.getString(Mediaplayer.KEY_TITLE));
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
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(MediaplayerCommandRequestHandler.PARAM_FILE, filePath));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_ADD), params);
	}

	/**
	 * delete from playlist
	 * 
	 * @param filePath
	 */
	private void deleteFromPlaylist(String filePath) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(MediaplayerCommandRequestHandler.PARAM_FILE, filePath));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_REMOVE), params);
	}

	/**
	 * play media file
	 * 
	 * @param filePath
	 */
	private void playFile(String filePath) {
		mMode = PLAY_MODE;

		// invalidate options menu
		getActionBarActivity().invalidateOptionsMenu();

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(MediaplayerCommandRequestHandler.PARAM_FILE, filePath));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_PLAY), params);
	}

	// play current media item
	private void play() {
		mMode = PLAY_MODE;

		// invalidate options menu
		getActionBarActivity().invalidateOptionsMenu();

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_PLAY));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);
	}

	// stop playing media item
	private void stop() {
		mMode = STOP_MODE;

		// invalidate options menu
		getActionBarActivity().invalidateOptionsMenu();

		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_STOP));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);

		deleteMediaInfo();
		ImageView imageView = (ImageView) getView().findViewById(R.id.cover);
		imageView.setImageResource(R.drawable.no_cover_art);
	}

	// play previous media item
	private void previous() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_PREVIOUS));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);
	}

	// play next media item
	private void next() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_NEXT));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);
	}

	// exit Mediaplayer
	private void exit() {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(MediaplayerCommandRequestHandler.PARAM_CMD,
				MediaplayerCommandRequestHandler.CMD_EXIT));

		execSimpleResultTask(new MediaplayerCommandRequestHandler(URIStore.MEDIA_PLAYER_CMD), params);
	}

	private void setArgs(String name, String value) {
		ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(name, value));

		if (args != null) {
			args.clear();
			args.putSerializable("params", params);
		} else {
			args = new Bundle();
			args.putSerializable("params", params);
		}
	}

	/**
	 * Initializes the <code>SimpleListAdapter</code>
	 */
	private void setAdapter() {
		mAdapter = new MediaListAdapter(getActionBarActivity(), mMapList);
		setListAdapter(mAdapter);
	}

	/**
	 * Check if DetailView is available for current device
	 */
	private boolean isDetailViewAvailable(View v) {
		LinearLayout detailLayout = (LinearLayout) v.findViewById(R.id.detailView);

		if (detailLayout != null) {
			return true;
		} else {
			return false;
		}
	}

	private String getCommandFromKeyState(String keyStateText) {
		String command = "";

		if (keyStateText.contains("next")) {
			command = "next";
		}

		if (keyStateText.contains("play")) {
			command = "play";
		}

		if (keyStateText.contains("previous")) {
			command = "previous";
		}

		if (keyStateText.startsWith("Playback")) {
			command = "play";
		}

		return command;
	}

	@Override
	public void onSimpleResult(boolean success, ExtendedHashMap result) {

		// crash after new DreamDroid Version
		/**
		 * if (mChoice != null) { mChoice.dismiss(); mChoice = null; }
		 **/

		super.onSimpleResult(success, result);

		if (Python.TRUE.equals(result.getString(SimpleResult.KEY_STATE))) {

			String command = getCommandFromKeyState(result.getString(SimpleResult.KEY_STATE_TEXT));

			// check if necessary command was executed and if detail view is
			// used
			// if
			// (result.getString(SimpleResult.KEY_STATE_TEXT).startsWith("Playback")
			// && isDetailViewAvailable()) {
			if (command.equals("next") || command.equals("play") || command.equals("previous")) {

				// add image to detail view
				ImageView imageView = (ImageView) getView().findViewById(R.id.cover);

				ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
				params.add(new BasicNameValuePair("file", "/tmp/.id3coverart"));

				String imageUrl = getHttpClient().buildUrl("/file?", params);

				// String imageUrl =
				// "http://192.168.2.100/file?file=/tmp/.id3coverart";
				imageLoader.displayImage(imageUrl, imageView);

				// add media info to detail view
				new GetCurrentMediaInfoTask().execute("");
			}

			reload();
		}
	}

	@Override
	public Loader<LoaderResult<ArrayList<ExtendedHashMap>>> onCreateLoader(int id, Bundle args) {
		AsyncListLoader loader = new AsyncListLoader(getActionBarActivity(), new MediaplayerListRequestHandler(),
				false, args);
		return loader;
	}

	@Override
	public void onDialogAction(int action, Object details, String dialogTag) {

		String filePath = mMedia.getString(Mediaplayer.KEY_SERVICE_REFERENCE);

		switch (action) {

		case Statics.ACTION_PLAY_MEDIA:
			playFile(filePath);
			break;

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

}
