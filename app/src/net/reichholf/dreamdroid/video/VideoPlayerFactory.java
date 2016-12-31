package net.reichholf.dreamdroid.video;

import android.content.Context;

/**
 * Created by Stephan on 31.12.2016.
 */

public class VideoPlayerFactory {
	static VideoPlayer sPlayer;

	public static void init(Context context) {
		VLCPlayer.init(context);
		sPlayer = new VLCPlayer();
	}

	public static void deinit() {
		if(sPlayer == null)
			return;
		sPlayer.deinit();
		sPlayer = null;
	}

	public static VideoPlayer getInstance() {
		return sPlayer;
	}
}
