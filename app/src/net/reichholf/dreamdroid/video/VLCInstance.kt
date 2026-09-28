package net.reichholf.dreamdroid.video

/*
 * VLCInstance.kt
 *
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

import android.content.Context
import org.videolan.libvlc.LibVLC

/** The process-wide [LibVLC], created on first use from the caller's application context. */
object VLCInstance {
    @Volatile
    private var libVLC: LibVLC? = null

    @Synchronized
    fun get(context: Context): LibVLC = libVLC
        ?: LibVLC(context.applicationContext, arrayListOf("--http-reconnect")).also {
            libVLC = it
        }
}
