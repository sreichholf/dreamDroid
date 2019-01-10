package net.reichholf.dreamdroid.video;

/*****************************************************************************
 * VLCInstance.java
 *****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

import android.content.Context;

import net.reichholf.dreamdroid.DreamDroid;

import org.videolan.libvlc.LibVLC;

import java.util.ArrayList;


public class VLCInstance {
	public final static String TAG = "VLC/UiTools/VLCInstance";

	private static LibVLC sLibVLC = null;

	/**
	 * A set of utility functions for the VLC application
	 */
	public synchronized static LibVLC get() throws IllegalStateException {
		if (sLibVLC == null) {
			final Context context = DreamDroid.getAppContext();
			ArrayList<String> options = new ArrayList<>();
			options.add("--http-reconnect");
			sLibVLC = new LibVLC(context, options);
		}
		return sLibVLC;
	}

	public static synchronized void restart() throws IllegalStateException {
		if (sLibVLC != null) {
			sLibVLC.release();
			sLibVLC = null;
			get();
		}
	}
}
