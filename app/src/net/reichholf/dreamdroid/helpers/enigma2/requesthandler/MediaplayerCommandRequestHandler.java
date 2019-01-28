/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid.helpers.enigma2.requesthandler;

import net.reichholf.dreamdroid.helpers.NameValuePair;
import net.reichholf.dreamdroid.helpers.SimpleHttpClient;
import net.reichholf.dreamdroid.helpers.enigma2.URIStore;

import java.util.ArrayList;

/**
 * @author sre
 * 
 */
public class MediaplayerCommandRequestHandler extends SimpleResultRequestHandler {
	public static final String PARAM_CMD = "command";
	public static final String PARAM_FILE = "file";
	public static final String PARAM_NAME = "name";
	public static final String PARAM_ROOT = "root";
	public static final String PARAM_PLAYLIST_PATH = "path";
	public static final String PARAM_PLAYLIST_TYPES = "types";

	public static final String CMD_PLAY = "play";
	public static final String CMD_PAUSE = "pause";
	public static final String CMD_PREVIOUS = "previous";
	public static final String CMD_NEXT = "next";
	public static final String CMD_STOP = "stop";
	public static final String CMD_EXIT = "exit";
	public static final String CMD_SHUFFLE = "shuffle";
	public static final String CMD_CLEAR = "clear";

	public static final String PATH_PLAYLIST = "playlist";
	public static final String PATH_FILESYSTEMS = "Filesystems";

	public static final String TYPE_AUDIO = "audio";
	public static final String TYPE_VIDEO = "video";
	public static final String TYPE_ANY = "any";

	public MediaplayerCommandRequestHandler() {
		super(null);
	}
	
	public MediaplayerCommandRequestHandler(String uri) {
		super(uri);
	}

	/**
	 * @param shc
	 * @param uri
	 * @param param
	 * @param value
	 * @return
	 */
	private String singleParamCall(SimpleHttpClient shc, String uri, String param, String value) {
		mUri = uri;
		ArrayList<NameValuePair> params = new ArrayList<>();
		params.add(new NameValuePair(param, value));
		return get(shc, params);
	}

	/**
	 * @param shc
	 * @param cmd
	 * @return
	 */
	private String cmd(SimpleHttpClient shc, String cmd) {
		return singleParamCall(shc, URIStore.MEDIA_PLAYER_CMD, PARAM_CMD, cmd);
	}

	/**
	 * @param shc
	 * @param params
	 * @return
	 */
	public String playFile(SimpleHttpClient shc, String file) {
		return singleParamCall(shc, URIStore.MEDIA_PLAYER_PLAY, PARAM_FILE, file);
	}

	/**
	 * @param shc
	 * @param file
	 * @return
	 */
	public String addToPlaylist(SimpleHttpClient shc, String file) {
		return singleParamCall(shc, URIStore.MEDIA_PLAYER_ADD, PARAM_FILE, file);
	}

	/**
	 * @param shc
	 * @param file
	 * @return
	 */
	public String removeFromPlaylist(SimpleHttpClient shc, String file) {
		return singleParamCall(shc, URIStore.MEDIA_PLAYER_REMOVE, PARAM_FILE, file);
	}

	/**
	 * @param shc
	 * @param name
	 * @return
	 */
	public String loadPlaylist(SimpleHttpClient shc, String name) {
		return singleParamCall(shc, URIStore.MEDIA_PLAYER_LOAD, PARAM_NAME, name);
	}

	/**
	 * @param shc
	 * @param name
	 * @return
	 */
	public String savePlaylist(SimpleHttpClient shc, String name) {
		return singleParamCall(shc, URIStore.MEDIA_PLAYER_WRITE, PARAM_NAME, name);
	}

	/**
	 * @param shc
	 * @return
	 */
	public String play(SimpleHttpClient shc) {
		return cmd(shc, CMD_PLAY);
	}

	/**
	 * @param shc
	 * @return
	 */
	public String pause(SimpleHttpClient shc) {
		return cmd(shc, CMD_PAUSE);
	}

	/**
	 * @param shc
	 * @return
	 */
	public String next(SimpleHttpClient shc) {
		return cmd(shc, CMD_NEXT);
	}

	/**
	 * @param shc
	 * @return
	 */
	public String previous(SimpleHttpClient shc) {
		return cmd(shc, CMD_PREVIOUS);
	}

	/**
	 * @param shc
	 * @return
	 */
	public String stop(SimpleHttpClient shc) {
		return cmd(shc, CMD_STOP);
	}

	/**
	 * @param shc
	 * @return
	 */
	public String shuffle(SimpleHttpClient shc) {
		return cmd(shc, CMD_SHUFFLE);
	}

	/**
	 * @param shc
	 * @return
	 */
	public String clear(SimpleHttpClient shc) {
		return cmd(shc, CMD_CLEAR);
	}

	/**
	 * @param shc
	 * @return
	 */
	public String exit(SimpleHttpClient shc) {
		return cmd(shc, CMD_EXIT);
	}
}
