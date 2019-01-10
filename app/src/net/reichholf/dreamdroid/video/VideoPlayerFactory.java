package net.reichholf.dreamdroid.video;

/**
 * Created by Stephan on 31.12.2016.
 */

public class VideoPlayerFactory {
	static VideoPlayer sPlayer;

	public static void release() {
		if(sPlayer == null)
			return;
		sPlayer.deinit();
		sPlayer = null;
	}

	public static VideoPlayer getInstance() {
		if (sPlayer == null)
			sPlayer = new VLCPlayer();
		return sPlayer;
	}
}
