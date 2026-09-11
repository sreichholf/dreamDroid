package net.reichholf.dreamdroid.video

/*****************************************************************************
 * VLCInstance.kt
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

import net.reichholf.dreamdroid.DreamDroid
import org.videolan.libvlc.LibVLC

/**
 * Thin Kotlin port of the LibVLC singleton holder (Phase 2.5e).
 * Behavior matches the former Java [VLCInstance].
 */
object VLCInstance {
    const val TAG = "VLC/UiTools/VLCInstance"

    @Volatile
    private var sLibVLC: LibVLC? = null

    @JvmStatic
    @Synchronized
    fun get(): LibVLC {
        var instance = sLibVLC
        if (instance == null) {
            val context = DreamDroid.getAppContext()
            val options = ArrayList<String>()
            options.add("--http-reconnect")
            instance = LibVLC(context, options)
            sLibVLC = instance
        }
        return instance
    }

    @JvmStatic
    @Synchronized
    fun restart() {
        val instance = sLibVLC
        if (instance != null) {
            instance.release()
            sLibVLC = null
            get()
        }
    }
}
