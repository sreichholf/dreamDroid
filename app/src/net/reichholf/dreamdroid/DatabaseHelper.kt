/*
 * © 2010 Stephan Reichholf <stephan at reichholf dot net>
 *
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */
package net.reichholf.dreamdroid

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.os.Environment
import android.util.Log
import net.reichholf.dreamdroid.helpers.ExtendedHashMap
import net.reichholf.dreamdroid.helpers.enigma2.Event
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel

/**
 * @author sre
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val mContext: Context = context

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(PROFILES_TABLE_CREATE)
        db.execSQL(EVENT_TABLE_CREATE)
        db.execSQL(SERVICES_TABLE_CREATE)
    }

    private fun upgrade6to7(db: SQLiteDatabase) {
        db.execSQL(PROFILES_TABLE_UPGRADE_6_7_1)
        db.execSQL(PROFILES_TABLE_UPGRADE_6_7_2)
        db.execSQL(PROFILES_TABLE_UPGRADE_6_7_3)
        db.execSQL(PROFILES_TABLE_UPGRADE_6_7_4)
    }

    private fun upgrade7to8(db: SQLiteDatabase) {
        db.execSQL(PROFILES_TABLE_UPGRADE_7_8_1)
        db.execSQL(PROFILES_TABLE_UPGRADE_7_8_2)
    }

    private fun upgrade11to12(db: SQLiteDatabase) {
        db.execSQL(PROFILES_TABLE_UPGRADE_11_12_1)
        db.execSQL(PROFILES_TABLE_UPGRADE_11_12_2)
        db.execSQL(PROFILES_TABLE_UPGRADE_11_12_3)
        db.execSQL(PROFILES_TABLE_UPGRADE_11_12_4)
        db.execSQL(PROFILES_TABLE_UPGRADE_11_12_5)
        db.execSQL(PROFILES_TABLE_UPGRADE_11_12_6)
        db.execSQL(PROFILES_TABLE_UPGRADE_11_12_7)
        db.execSQL(PROFILES_TABLE_UPGRADE_11_12_8)
    }

    private fun upgrade12to13(db: SQLiteDatabase) {
        db.execSQL(PROFILES_TABLE_UPGRADE_12_13_1)
        db.execSQL(PROFILES_TABLE_UPGRADE_12_13_2)
    }

    private fun upgrade13to14(db: SQLiteDatabase) {
        db.execSQL(PROFILES_TABLE_UPGRADE_13_14)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        var old = oldVersion
        val scheduleBackup = old < newVersion
        try {
            if (old == 2) {
                db.execSQL(PROFILES_TABLE_UPGRADE_2_3)
                old++
            }
            if (old == 3) {
                db.execSQL(PROFILES_TABLE_UPGRADE_3_4)
                old++
            }
            if (old == 4) {
                db.execSQL(PROFILES_TABLE_UPGRADE_4_5)
                old++
            }
            if (old == 5) {
                db.execSQL(PROFILES_TABLE_UPGRADE_5_6)
                old++
            }
            if (old == 6) {
                upgrade6to7(db)
                old++
            }
            if (old == 7) {
                upgrade7to8(db)
                old++
            }
            if (old == 8) {
                db.execSQL(PROFILES_TABLE_UPGRADE_8_9)
                old++
            }
            if (old == 9) {
                db.execSQL(EVENT_TABLE_CREATE)
                db.execSQL(SERVICES_TABLE_CREATE)
                old += 2
            }
            if (old == 10) { // DEVELOPMENT VERSIONS ONLY
                db.execSQL("DROP TABLE EPG;")
                db.execSQL(EVENT_TABLE_CREATE)
                old++
            }
            if (old == 11) {
                upgrade11to12(db)
                old++
            }
            if (old == 12) {
                upgrade12to13(db)
                old++
            }
            if (old == 13) {
                upgrade13to14(db)
                old++
            }
        } catch (e: SQLiteException) {
            Log.e(LOG_TAG, "onUpgrade: SQLiteException, recreating db. ", e)
            Log.e(LOG_TAG, "(oldVersion was $old)")
            emergencyRecovery(db)
            return // this was lossy
        }
        if (scheduleBackup) {
            DreamDroid.scheduleBackup(mContext)
        }
    }

    private fun emergencyRecovery(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS $PROFILES_TABLE_NAME;")
        db.execSQL(PROFILES_TABLE_CREATE)
    }

    private fun p2cv(p: Profile): ContentValues {
        val values = ContentValues()
        values.put(KEY_PROFILE_PROFILE, p.getName())
        values.put(KEY_PROFILE_HOST, p.getHost())
        values.put(KEY_PROFILE_STREAM_HOST, p.getStreamHostValue())
        values.put(KEY_PROFILE_PORT, p.getPort())
        values.put(KEY_PROFILE_STREAM_PORT, p.getStreamPort())
        values.put(KEY_PROFILE_FILE_PORT, p.getFilePort())
        values.put(KEY_PROFILE_LOGIN, p.isLogin())
        values.put(KEY_PROFILE_USER, p.getUser())
        values.put(KEY_PROFILE_PASS, p.getPass())
        values.put(KEY_PROFILE_SSL, p.isSsl())
        values.put(KEY_PROFILE_TRUST_ALL_CERTS, p.isAllCertsTrusted())
        values.put(KEY_PROFILE_STREAM_LOGIN, p.isStreamLogin())
        values.put(KEY_PROFILE_FILE_LOGIN, p.isFileLogin())
        values.put(KEY_PROFILE_FILE_SSL, p.isFileSsl())
        values.put(KEY_PROFILE_SIMPLE_REMOTE, p.isSimpleRemote())
        values.put(KEY_PROFILE_DEFAULT_REF, p.getDefaultBouquetTv())
        values.put(KEY_PROFILE_DEFAULT_REF_NAME, p.getDefaultBouquetTvName())
        values.put(KEY_PROFILE_DEFAULT_REF_2, p.getParentBouquetTv())
        values.put(KEY_PROFILE_DEFAULT_REF_2_NAME, p.getParentBouquetTvName())
        values.put(KEY_PROFILE_ENCODER_STREAM, p.isEncoderStream())
        values.put(KEY_PROFILE_ENCODER_PATH, p.getEncoderPath())
        values.put(KEY_PROFILE_ENCODER_PORT, p.getEncoderPort())
        values.put(KEY_PROFILE_ENCODER_LOGIN, p.isEncoderLogin())
        values.put(KEY_PROFILE_ENCODER_USER, p.getEncoderUser())
        values.put(KEY_PROFILE_ENCODER_PASS, p.getEncoderPass())
        values.put(KEY_PROFILE_ENCODER_VIDEO_BITRATE, p.getEncoderVideoBitrate())
        values.put(KEY_PROFILE_ENCODER_AUDIO_BITRATE, p.getEncoderAudioBitrate())
        values.put(KEY_SSID, p.getSsid())
        values.put(KEY_DEFAULT_PROFILE_ON_NO_WIFI, p.isDefaultProfileOnNoWifi())
        return values
    }

    /**
     * @param p
     */
    fun addProfile(p: Profile): Boolean {
        val db = writableDatabase
        val id = db.insert(PROFILES_TABLE_NAME, null, p2cv(p))
        if (id > -1) {
            db.close()
            p.setId(id.toInt())
            DreamDroid.scheduleBackup(mContext)
            return true
        }
        db.close()
        return false
    }

    /**
     * @param p
     */
    fun updateProfile(p: Profile): Boolean {
        val db = writableDatabase
        val numRows = db.update(PROFILES_TABLE_NAME, p2cv(p), KEY_PROFILE_ID + "=" + p.getId(), null)
        db.close()
        if (numRows == 1) {
            DreamDroid.scheduleBackup(mContext)
            DreamDroid.profileChanged(mContext, p)
            return true
        }
        return false
    }

    /**
     * @param p
     */
    fun deleteProfile(p: Profile): Boolean {
        val db = writableDatabase
        val numRows = db.delete(PROFILES_TABLE_NAME, KEY_PROFILE_ID + "=" + p.getId(), null)
        db.close()
        if (numRows == 1) {
            DreamDroid.scheduleBackup(mContext)
            return true
        }
        return false
    }

    /**
     * @return Profile for all Settings
     */
    fun getProfiles(): ArrayList<Profile> {
        val columns = arrayOf(
            KEY_PROFILE_ID, KEY_PROFILE_PROFILE, KEY_PROFILE_HOST, KEY_PROFILE_STREAM_HOST, KEY_PROFILE_PORT,
            KEY_PROFILE_STREAM_PORT, KEY_PROFILE_FILE_PORT, KEY_PROFILE_LOGIN, KEY_PROFILE_USER, KEY_PROFILE_PASS,
            KEY_PROFILE_SSL, KEY_PROFILE_TRUST_ALL_CERTS, KEY_PROFILE_SIMPLE_REMOTE, KEY_PROFILE_STREAM_LOGIN,
            KEY_PROFILE_FILE_LOGIN, KEY_PROFILE_FILE_SSL, KEY_PROFILE_DEFAULT_REF, KEY_PROFILE_DEFAULT_REF_NAME,
            KEY_PROFILE_DEFAULT_REF_2, KEY_PROFILE_DEFAULT_REF_2_NAME,
            KEY_PROFILE_ENCODER_STREAM, KEY_PROFILE_ENCODER_PATH, KEY_PROFILE_ENCODER_PORT, KEY_PROFILE_ENCODER_LOGIN,
            KEY_PROFILE_ENCODER_USER, KEY_PROFILE_ENCODER_PASS, KEY_PROFILE_ENCODER_VIDEO_BITRATE,
            KEY_PROFILE_ENCODER_AUDIO_BITRATE,
            KEY_SSID, KEY_DEFAULT_PROFILE_ON_NO_WIFI,
        )
        val db = readableDatabase

        val list = ArrayList<Profile>()

        val c = db.query(PROFILES_TABLE_NAME, columns, null, null, null, null, KEY_PROFILE_PROFILE)
        if (c.count == 0) {
            db.close()
            c.close()
            return list
        }

        while (!c.isLast) {
            c.moveToNext()
            list.add(getProfileFrom(c))
        }
        c.close()
        db.close()
        return list
    }

    /**
     * @param id
     * @return the profile for the given id, or null if no such profile was found
     */
    fun getProfile(id: Int): Profile? {
        val columns = arrayOf(
            KEY_PROFILE_ID, KEY_PROFILE_PROFILE, KEY_PROFILE_HOST, KEY_PROFILE_STREAM_HOST, KEY_PROFILE_PORT,
            KEY_PROFILE_STREAM_PORT, KEY_PROFILE_FILE_PORT, KEY_PROFILE_LOGIN, KEY_PROFILE_USER, KEY_PROFILE_PASS,
            KEY_PROFILE_SSL, KEY_PROFILE_SIMPLE_REMOTE, KEY_PROFILE_STREAM_LOGIN, KEY_PROFILE_FILE_LOGIN,
            KEY_PROFILE_FILE_SSL, KEY_PROFILE_TRUST_ALL_CERTS, KEY_PROFILE_DEFAULT_REF, KEY_PROFILE_DEFAULT_REF_NAME,
            KEY_PROFILE_DEFAULT_REF_2, KEY_PROFILE_DEFAULT_REF_2_NAME,
            KEY_PROFILE_ENCODER_STREAM, KEY_PROFILE_ENCODER_PATH, KEY_PROFILE_ENCODER_PORT, KEY_PROFILE_ENCODER_LOGIN,
            KEY_PROFILE_ENCODER_USER, KEY_PROFILE_ENCODER_PASS, KEY_PROFILE_ENCODER_VIDEO_BITRATE,
            KEY_PROFILE_ENCODER_AUDIO_BITRATE,
            KEY_SSID, KEY_DEFAULT_PROFILE_ON_NO_WIFI,
        )
        val db = readableDatabase
        val c = db.query(
            PROFILES_TABLE_NAME, columns, KEY_PROFILE_ID + "=" + id, null, null, null, KEY_PROFILE_PROFILE,
        )

        var p: Profile? = null
        if (c.count == 1 && c.moveToFirst()) {
            p = getProfileFrom(c)
        }
        c.close()
        db.close()
        return p
    }

    private fun getProfileFrom(c: Cursor): Profile {
        val id = c.getInt(c.getColumnIndex(KEY_PROFILE_ID))
        val name = c.getString(c.getColumnIndex(KEY_PROFILE_PROFILE))
        val host = c.getString(c.getColumnIndex(KEY_PROFILE_HOST))
        val streamHost = c.getString(c.getColumnIndex(KEY_PROFILE_STREAM_HOST))
        val port = c.getInt(c.getColumnIndex(KEY_PROFILE_PORT))

        var streamPort = c.getInt(c.getColumnIndex(KEY_PROFILE_STREAM_PORT))
        if (streamPort <= 0) {
            streamPort = 8001
        }

        var filePort = c.getInt(c.getColumnIndex(KEY_PROFILE_FILE_PORT))
        if (filePort <= 0) {
            filePort = 80
        }

        val isLogin = c.getInt(c.getColumnIndex(KEY_PROFILE_LOGIN)) == 1
        val isSsl = c.getInt(c.getColumnIndex(KEY_PROFILE_SSL)) == 1
        val isAllCertsTrusted = c.getInt(c.getColumnIndex(KEY_PROFILE_TRUST_ALL_CERTS)) == 1
        val isStreamLogin = c.getInt(c.getColumnIndex(KEY_PROFILE_STREAM_LOGIN)) == 1
        val isFileLogin = c.getInt(c.getColumnIndex(KEY_PROFILE_FILE_LOGIN)) == 1
        val isFileSsl = c.getInt(c.getColumnIndex(KEY_PROFILE_FILE_SSL)) == 1
        val isSimpleRemote = c.getInt(c.getColumnIndex(KEY_PROFILE_SIMPLE_REMOTE)) == 1

        val user = c.getString(c.getColumnIndex(KEY_PROFILE_USER))
        val pass = c.getString(c.getColumnIndex(KEY_PROFILE_PASS))

        val defaultRef = c.getString(c.getColumnIndex(KEY_PROFILE_DEFAULT_REF))
        val defaultRefName = c.getString(c.getColumnIndex(KEY_PROFILE_DEFAULT_REF_NAME))

        val defaultRef2 = c.getString(c.getColumnIndex(KEY_PROFILE_DEFAULT_REF_2))
        val defaultRef2Name = c.getString(c.getColumnIndex(KEY_PROFILE_DEFAULT_REF_2_NAME))

        val isEncoderStream = c.getInt(c.getColumnIndex(KEY_PROFILE_ENCODER_STREAM)) == 1
        var encoderPath = c.getString(c.getColumnIndex(KEY_PROFILE_ENCODER_PATH))
        var encoderPort = c.getInt(c.getColumnIndex(KEY_PROFILE_ENCODER_PORT))
        val isEncoderLogin = c.getInt(c.getColumnIndex(KEY_PROFILE_ENCODER_LOGIN)) == 1
        var encoderUser = c.getString(c.getColumnIndex(KEY_PROFILE_ENCODER_USER))
        var encoderPass = c.getString(c.getColumnIndex(KEY_PROFILE_ENCODER_PASS))
        var encoderAudioBitrate = c.getInt(c.getColumnIndex(KEY_PROFILE_ENCODER_AUDIO_BITRATE))
        var encoderVideoBitrate = c.getInt(c.getColumnIndex(KEY_PROFILE_ENCODER_VIDEO_BITRATE))

        val ssid = c.getString(c.getColumnIndex(KEY_SSID))
        val defaultProfileOnNoWifi = c.getInt(c.getColumnIndex(KEY_DEFAULT_PROFILE_ON_NO_WIFI)) == 1

        encoderPath = encoderPath ?: "stream"
        encoderPort = if (encoderPort <= 0) 554 else encoderPort
        encoderUser = encoderUser ?: ""
        encoderPass = encoderPass ?: ""
        encoderAudioBitrate = if (encoderAudioBitrate <= 0) 128 else encoderAudioBitrate
        encoderVideoBitrate = if (encoderVideoBitrate <= 0) 2500 else encoderVideoBitrate

        val p = Profile(
            id, name, host, streamHost, port, streamPort, filePort, isLogin, user, pass, isSsl,
            isAllCertsTrusted, isStreamLogin, isFileLogin, isFileSsl, isSimpleRemote, defaultRef,
            defaultRefName, defaultRef2, defaultRef2Name, isEncoderStream, encoderPath, encoderPort,
            isEncoderLogin, encoderUser, encoderPass, encoderVideoBitrate, encoderAudioBitrate,
        )
        p.setSsid(ssid)
        p.setDefaultProfileOnNoWifi(defaultProfileOnNoWifi)
        return p
    }

    fun setEvents(events: ArrayList<ExtendedHashMap>): Int {
        val db = writableDatabase
        db.beginTransaction()
        var success = 0
        for (event in events) {
            if (setEvent(event, db)) {
                success++
            }
        }
        db.endTransaction()
        db.close()
        return success
    }

    fun setEvent(event: ExtendedHashMap, db: SQLiteDatabase): Boolean {
        val values = eventToCv(event) ?: return false

        val id = values.getAsString(KEY_EVENT_ID)
        db.delete(EVENT_TABLE_NAME, "$KEY_EVENT_ID=?;", arrayOf(id))
        return db.insert(EVENT_TABLE_NAME, null, values) > -1
    }

    fun eventToCv(event: ExtendedHashMap): ContentValues? {
        val values = ContentValues()
        val _id: Int
        val _start: Int
        val _duration: Int
        try {
            _id = Integer.parseInt(event.getString(Event.KEY_EVENT_ID))
            _start = Integer.parseInt(event.getString(Event.KEY_EVENT_START))
            _duration = Integer.parseInt(event.getString(Event.KEY_EVENT_DURATION))
        } catch (nex: NumberFormatException) {
            return null
        }

        values.put(KEY_EVENT_ID, _id)
        values.put(KEY_EVENT_START, _start)
        values.put(KEY_EVENT_DURATION, _duration)
        values.put(KEY_EVENT_TITLE, event.getString(Event.KEY_EVENT_TITLE))
        values.put(KEY_EVENT_DESCRIPTION, event.getString(Event.KEY_EVENT_DESCRIPTION))
        values.put(KEY_EVENT_DESCRIPTION_EXTENDED, event.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED))
        values.put(KEY_EVENT_SERVICE_REFERENCE, event.getString(Event.KEY_SERVICE_REFERENCE))
        return values
    }

    fun exportDB(): Boolean {
        val sd = Environment.getExternalStorageDirectory()
        val data = Environment.getDataDirectory()
        var source: FileChannel? = null
        var destination: FileChannel? = null
        val currentDBPath = "/data/net.reichholf.dreamdroid/databases/$DATABASE_NAME"
        val backupDBPath = "$DATABASE_NAME.sqlite"
        val currentDB = File(data, currentDBPath)
        val backupDB = File(sd, backupDBPath)
        try {
            source = FileInputStream(currentDB).channel
            destination = FileOutputStream(backupDB).channel
            destination.transferFrom(source, 0, source.size())
            source.close()
            destination.close()
            return true
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    companion object {
        private const val DATABASE_VERSION = 14

        @JvmField
        val LOG_TAG: String = DatabaseHelper::class.java.simpleName

        @JvmField
        val KEY_PROFILE_ID = "_id"

        @JvmField
        val KEY_PROFILE_PROFILE = "profile"

        @JvmField
        val KEY_PROFILE_HOST = "host"

        @JvmField
        val KEY_PROFILE_STREAM_HOST = "streamhost"

        @JvmField
        val KEY_PROFILE_STREAM_PORT = "streamport"

        @JvmField
        val KEY_PROFILE_STREAM_LOGIN = "streamlogin"

        @JvmField
        val KEY_PROFILE_FILE_PORT = "fileport"

        @JvmField
        val KEY_PROFILE_PORT = "port"

        @JvmField
        val KEY_PROFILE_LOGIN = "login"

        @JvmField
        val KEY_PROFILE_USER = "user"

        @JvmField
        val KEY_PROFILE_PASS = "pass"

        @JvmField
        val KEY_PROFILE_SSL = "ssl"

        @JvmField
        val KEY_PROFILE_FILE_SSL = "file_ssl"

        @JvmField
        val KEY_PROFILE_FILE_LOGIN = "file_login"

        @JvmField
        val KEY_PROFILE_SIMPLE_REMOTE = "simpleremote"

        @JvmField
        val KEY_PROFILE_DEFAULT_REF = "default_ref"

        @JvmField
        val KEY_PROFILE_DEFAULT_REF_NAME = "default_ref_name"

        @JvmField
        val KEY_PROFILE_DEFAULT_REF_2 = "default_ref_2"

        @JvmField
        val KEY_PROFILE_DEFAULT_REF_2_NAME = "default_ref_2_name"

        @JvmField
        val KEY_SSID = "ssid"

        @JvmField
        val KEY_DEFAULT_PROFILE_ON_NO_WIFI = "defaultProfileOnNoWifi"

        // ENCODER
        @JvmField
        val KEY_PROFILE_ENCODER_STREAM = "encoder_stream"

        @JvmField
        val KEY_PROFILE_ENCODER_PATH = "encoder_path"

        @JvmField
        val KEY_PROFILE_ENCODER_PORT = "encoder_port"

        @JvmField
        val KEY_PROFILE_ENCODER_LOGIN = "encoder_login"

        @JvmField
        val KEY_PROFILE_ENCODER_USER = "encoder_user"

        @JvmField
        val KEY_PROFILE_ENCODER_PASS = "encoder_pass"

        @JvmField
        val KEY_PROFILE_ENCODER_VIDEO_BITRATE = "encoder_video_bitrate"

        @JvmField
        val KEY_PROFILE_ENCODER_AUDIO_BITRATE = "encoder_audio_bitrate"

        @JvmField
        val KEY_PROFILE_TRUST_ALL_CERTS = "trust_all_certs"

        @JvmField
        val KEY_EVENT_ID = "id"

        @JvmField
        val KEY_EVENT_START = "start"

        @JvmField
        val KEY_EVENT_DURATION = "duration"

        @JvmField
        val KEY_EVENT_TITLE = "title"

        @JvmField
        val KEY_EVENT_DESCRIPTION = "description"

        @JvmField
        val KEY_EVENT_DESCRIPTION_EXTENDED = "description_ext"

        @JvmField
        val KEY_EVENT_SERVICE_REFERENCE = "sid"

        @JvmField
        val KEY_SERVICES_REFERENCE = "ref"

        @JvmField
        val KEY_SERVICES_NAME = "name"

        @JvmField
        val DATABASE_NAME = "dreamdroid"

        private const val PROFILES_TABLE_NAME = "profiles"
        private const val EVENT_TABLE_NAME = "events"
        private const val SERVICES_TABLE_NAME = "services"

        private val PROFILES_TABLE_CREATE =
            "CREATE TABLE IF NOT EXISTS " +
                PROFILES_TABLE_NAME + " (" +
                KEY_PROFILE_ID + " INTEGER PRIMARY KEY, " +
                KEY_PROFILE_PROFILE + " TEXT, " +
                KEY_PROFILE_HOST + " TEXT, " +
                KEY_PROFILE_STREAM_HOST + " TEXT, " +
                KEY_PROFILE_PORT + " INTEGER, " +
                KEY_PROFILE_STREAM_PORT + " INTEGER, " +
                KEY_PROFILE_FILE_PORT + " INTEGER, " +
                KEY_PROFILE_LOGIN + " BOOLEAN, " +
                KEY_PROFILE_USER + " TEXT, " +
                KEY_PROFILE_PASS + " TEXT, " +
                KEY_PROFILE_SSL + " BOOLEAN, " +
                KEY_PROFILE_SIMPLE_REMOTE + " BOOLEAN, " +
                KEY_PROFILE_DEFAULT_REF + " TEXT, " +
                KEY_PROFILE_DEFAULT_REF_NAME + " TEXT, " +
                KEY_PROFILE_DEFAULT_REF_2 + " TEXT, " +
                KEY_PROFILE_DEFAULT_REF_2_NAME + " TEXT, " +
                KEY_PROFILE_FILE_LOGIN + " BOOLEAN, " +
                KEY_PROFILE_FILE_SSL + " BOOLEAN, " +
                KEY_PROFILE_STREAM_LOGIN + " BOOLEAN, " +
                KEY_PROFILE_ENCODER_STREAM + " BOOLEAN, " +
                KEY_PROFILE_ENCODER_PATH + " TEXT, " +
                KEY_PROFILE_ENCODER_PORT + " INTEGER, " +
                KEY_PROFILE_ENCODER_LOGIN + " BOOLEAN, " +
                KEY_PROFILE_ENCODER_USER + " TEXT, " +
                KEY_PROFILE_ENCODER_PASS + " TEXT, " +
                KEY_PROFILE_ENCODER_VIDEO_BITRATE + " TEXT, " +
                KEY_SSID + " TEXT, " +
                KEY_DEFAULT_PROFILE_ON_NO_WIFI + " BOOLEAN, " +
                KEY_PROFILE_ENCODER_AUDIO_BITRATE + " TEXT, " +
                KEY_PROFILE_TRUST_ALL_CERTS + " BOOLEAN);"

        private val PROFILES_TABLE_UPGRADE_2_3 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_STREAM_HOST TEXT;"

        private val PROFILES_TABLE_UPGRADE_3_4 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_SIMPLE_REMOTE BOOLEAN;"

        private val PROFILES_TABLE_UPGRADE_4_5 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_STREAM_PORT INTEGER;"

        private val PROFILES_TABLE_UPGRADE_5_6 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_FILE_PORT INTEGER;"

        private val PROFILES_TABLE_UPGRADE_6_7_1 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_DEFAULT_REF TEXT;"
        private val PROFILES_TABLE_UPGRADE_6_7_2 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_DEFAULT_REF_NAME TEXT;"
        private val PROFILES_TABLE_UPGRADE_6_7_3 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_DEFAULT_REF_2 TEXT;"
        private val PROFILES_TABLE_UPGRADE_6_7_4 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_DEFAULT_REF_2_NAME TEXT;"

        private val PROFILES_TABLE_UPGRADE_7_8_1 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_FILE_LOGIN BOOLEAN;"
        private val PROFILES_TABLE_UPGRADE_7_8_2 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_FILE_SSL BOOLEAN;"

        private val PROFILES_TABLE_UPGRADE_8_9 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_STREAM_LOGIN BOOLEAN;"

        private val PROFILES_TABLE_UPGRADE_11_12_1 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_ENCODER_STREAM BOOLEAN;"
        private val PROFILES_TABLE_UPGRADE_11_12_2 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_ENCODER_PATH TEXT;"
        private val PROFILES_TABLE_UPGRADE_11_12_3 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_ENCODER_PORT INTEGER;"
        private val PROFILES_TABLE_UPGRADE_11_12_4 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_ENCODER_LOGIN BOOLEAN;"
        private val PROFILES_TABLE_UPGRADE_11_12_5 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_ENCODER_USER TEXT;"
        private val PROFILES_TABLE_UPGRADE_11_12_6 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_ENCODER_PASS TEXT;"
        private val PROFILES_TABLE_UPGRADE_11_12_7 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_ENCODER_VIDEO_BITRATE INTEGER;"
        private val PROFILES_TABLE_UPGRADE_11_12_8 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_ENCODER_AUDIO_BITRATE INTEGER;"

        private val PROFILES_TABLE_UPGRADE_12_13_1 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_SSID TEXT; "
        private val PROFILES_TABLE_UPGRADE_12_13_2 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_DEFAULT_PROFILE_ON_NO_WIFI BOOLEAN;"

        private val PROFILES_TABLE_UPGRADE_13_14 =
            "ALTER TABLE $PROFILES_TABLE_NAME ADD $KEY_PROFILE_TRUST_ALL_CERTS BOOLEAN;"

        private val EVENT_TABLE_CREATE =
            "CREATE TABLE IF NOT EXISTS " +
                EVENT_TABLE_NAME + " (" +
                KEY_EVENT_ID + " INTEGER PRIMARY KEY, " +
                KEY_EVENT_START + " INTEGER, " +
                KEY_EVENT_DURATION + " INTEGER, " +
                KEY_EVENT_TITLE + " TEXT, " +
                KEY_EVENT_DESCRIPTION + " TEXT, " +
                KEY_EVENT_DESCRIPTION_EXTENDED + " TEXT, " +
                KEY_EVENT_SERVICE_REFERENCE + " TEXT);"

        private val SERVICES_TABLE_CREATE =
            "CREATE TABLE IF NOT EXISTS " +
                SERVICES_TABLE_NAME + " (" +
                KEY_SERVICES_REFERENCE + " TEXT PRIMARY KEY, " +
                KEY_SERVICES_NAME + " TEXT);"

        @JvmStatic
        fun getInstance(ctx: Context): DatabaseHelper {
            return DatabaseHelper(ctx)
        }
    }
}
