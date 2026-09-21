/*
 * © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */
package net.reichholf.dreamdroid

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.reichholf.dreamdroid.room.AppDatabase

/**
 * Pre-Room `dreamdroid` SQLite. Read-only profile import for Play 1.x upgrades and
 * cloud-restore snapshots that still have this file. Never creates the database.
 */
object DatabaseHelper {
    const val DATABASE_NAME: String = "dreamdroid"

    val LOG_TAG: String = DatabaseHelper::class.java.simpleName

    private const val PROFILES_TABLE = "profiles"

    private const val KEY_ID = "_id"
    private const val KEY_PROFILE = "profile"
    private const val KEY_HOST = "host"
    private const val KEY_STREAM_HOST = "streamhost"
    private const val KEY_STREAM_PORT = "streamport"
    private const val KEY_FILE_PORT = "fileport"
    private const val KEY_PORT = "port"
    private const val KEY_LOGIN = "login"
    private const val KEY_USER = "user"
    private const val KEY_PASS = "pass"
    private const val KEY_SSL = "ssl"
    private const val KEY_FILE_SSL = "file_ssl"
    private const val KEY_FILE_LOGIN = "file_login"
    private const val KEY_STREAM_LOGIN = "streamlogin"
    private const val KEY_SIMPLE_REMOTE = "simpleremote"
    private const val KEY_DEFAULT_REF = "default_ref"
    private const val KEY_DEFAULT_REF_NAME = "default_ref_name"
    private const val KEY_DEFAULT_REF_2 = "default_ref_2"
    private const val KEY_DEFAULT_REF_2_NAME = "default_ref_2_name"
    private const val KEY_SSID = "ssid"
    private const val KEY_DEFAULT_ON_NO_WIFI = "defaultProfileOnNoWifi"
    private const val KEY_ENCODER_STREAM = "encoder_stream"
    private const val KEY_ENCODER_PATH = "encoder_path"
    private const val KEY_ENCODER_PORT = "encoder_port"
    private const val KEY_ENCODER_LOGIN = "encoder_login"
    private const val KEY_ENCODER_USER = "encoder_user"
    private const val KEY_ENCODER_PASS = "encoder_pass"
    private const val KEY_ENCODER_VIDEO_BITRATE = "encoder_video_bitrate"
    private const val KEY_ENCODER_AUDIO_BITRATE = "encoder_audio_bitrate"
    private const val KEY_TRUST_ALL_CERTS = "trust_all_certs"

    fun databaseFile(context: Context): File = context.getDatabasePath(DATABASE_NAME)

    /**
     * Profiles from an existing leftover file. Empty if the file is missing or
     * unreadable. Does not create `dreamdroid`.
     */
    fun readProfiles(context: Context): List<Profile> {
        val file = databaseFile(context)
        if (!file.exists()) {
            return emptyList()
        }
        return try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                readProfiles(db)
            }
        } catch (e: SQLiteException) {
            Log.e(LOG_TAG, "readProfiles: cannot open leftover $DATABASE_NAME", e)
            emptyList()
        }
    }

    /**
     * When Room has no profiles, copy leftover rows in and delete the file.
     * No-op if Room already has rows or the leftover file is missing.
     * Deletes an existing leftover after a successful open, including 0 rows
     * (empty file created by the old [SQLiteOpenHelper] path).
     */
    fun migrateIntoRoomIfNeeded(
        context: Context,
        dao: Profile.ProfileDao = AppDatabase.profiles(context)
    ): Int = runBlocking(Dispatchers.IO) {
        if (dao.getProfiles().isNotEmpty()) {
            return@runBlocking 0
        }
        val file = databaseFile(context)
        if (!file.exists()) {
            return@runBlocking 0
        }
        val profiles = try {
            SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                readProfiles(db)
            }
        } catch (e: SQLiteException) {
            Log.e(LOG_TAG, "migrateIntoRoomIfNeeded: leftover unreadable", e)
            return@runBlocking 0
        }
        for (profile in profiles) {
            profile.id = dao.addProfile(profile).toInt()
        }
        context.deleteDatabase(DATABASE_NAME)
        profiles.size
    }

    private fun readProfiles(db: SQLiteDatabase): List<Profile> {
        val list = ArrayList<Profile>()
        db.query(
            PROFILES_TABLE,
            null,
            null,
            null,
            null,
            null,
            KEY_PROFILE
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(profileFrom(cursor))
            }
        }
        return list
    }

    private fun profileFrom(c: Cursor): Profile {
        var streamPort = c.intOr(KEY_STREAM_PORT, 0)
        if (streamPort <= 0) {
            streamPort = 8001
        }
        var filePort = c.intOr(KEY_FILE_PORT, 0)
        if (filePort <= 0) {
            filePort = 80
        }
        var encoderPath = c.stringOr(KEY_ENCODER_PATH)
        var encoderPort = c.intOr(KEY_ENCODER_PORT, 0)
        var encoderUser = c.stringOr(KEY_ENCODER_USER)
        var encoderPass = c.stringOr(KEY_ENCODER_PASS)
        var encoderAudioBitrate = c.intOr(KEY_ENCODER_AUDIO_BITRATE, 0)
        var encoderVideoBitrate = c.intOr(KEY_ENCODER_VIDEO_BITRATE, 0)
        encoderPath = encoderPath ?: "stream"
        encoderPort = if (encoderPort <= 0) 554 else encoderPort
        encoderUser = encoderUser ?: ""
        encoderPass = encoderPass ?: ""
        encoderAudioBitrate = if (encoderAudioBitrate <= 0) 128 else encoderAudioBitrate
        encoderVideoBitrate = if (encoderVideoBitrate <= 0) 2500 else encoderVideoBitrate

        val profile = Profile(
            c.intOr(KEY_ID, 0).takeIf { it > 0 },
            c.stringOr(KEY_PROFILE),
            c.stringOr(KEY_HOST),
            c.stringOr(KEY_STREAM_HOST),
            c.intOr(KEY_PORT, 0),
            streamPort,
            filePort,
            c.boolOr(KEY_LOGIN),
            c.stringOr(KEY_USER),
            c.stringOr(KEY_PASS),
            c.boolOr(KEY_SSL),
            c.boolOr(KEY_TRUST_ALL_CERTS),
            c.boolOr(KEY_STREAM_LOGIN),
            c.boolOr(KEY_FILE_LOGIN),
            c.boolOr(KEY_FILE_SSL),
            c.boolOr(KEY_SIMPLE_REMOTE),
            c.stringOr(KEY_DEFAULT_REF),
            c.stringOr(KEY_DEFAULT_REF_NAME),
            c.stringOr(KEY_DEFAULT_REF_2),
            c.stringOr(KEY_DEFAULT_REF_2_NAME),
            c.boolOr(KEY_ENCODER_STREAM),
            encoderPath,
            encoderPort,
            c.boolOr(KEY_ENCODER_LOGIN),
            encoderUser,
            encoderPass,
            encoderVideoBitrate,
            encoderAudioBitrate
        )
        profile.ssid = c.stringOr(KEY_SSID)
        profile.isDefaultProfileOnNoWifi = c.boolOr(KEY_DEFAULT_ON_NO_WIFI)
        return profile
    }

    private fun Cursor.intOr(name: String, default: Int): Int {
        val index = getColumnIndex(name)
        if (index < 0 || isNull(index)) {
            return default
        }
        return getInt(index)
    }

    private fun Cursor.stringOr(name: String): String? {
        val index = getColumnIndex(name)
        if (index < 0 || isNull(index)) {
            return null
        }
        return getString(index)
    }

    private fun Cursor.boolOr(name: String): Boolean = intOr(name, 0) == 1
}
