/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.util.Log;

import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Event;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;

/**
 * @author sre
 *
 */
public class DatabaseHelper extends SQLiteOpenHelper {
	public static final String LOG_TAG = DatabaseHelper.class.getSimpleName();

	public static final String KEY_PROFILE_ID = "_id";
	public static final String KEY_PROFILE_PROFILE = "profile";
	public static final String KEY_PROFILE_HOST = "host";
	public static final String KEY_PROFILE_STREAM_HOST = "streamhost";
	public static final String KEY_PROFILE_STREAM_PORT = "streamport";
	public static final String KEY_PROFILE_STREAM_LOGIN = "streamlogin";
	public static final String KEY_PROFILE_FILE_PORT = "fileport";
	public static final String KEY_PROFILE_PORT = "port";
	public static final String KEY_PROFILE_LOGIN = "login";
	public static final String KEY_PROFILE_USER = "user";
	public static final String KEY_PROFILE_PASS = "pass";
	public static final String KEY_PROFILE_SSL = "ssl";
	public static final String KEY_PROFILE_FILE_SSL = "file_ssl";
	public static final String KEY_PROFILE_FILE_LOGIN = "file_login";
	public static final String KEY_PROFILE_SIMPLE_REMOTE = "simpleremote";
	public static final String KEY_PROFILE_DEFAULT_REF = "default_ref";
	public static final String KEY_PROFILE_DEFAULT_REF_NAME = "default_ref_name";
	public static final String KEY_PROFILE_DEFAULT_REF_2 = "default_ref_2";
	public static final String KEY_PROFILE_DEFAULT_REF_2_NAME = "default_ref_2_name";

	public static final String KEY_EVENT_ID = "id";
	public static final String KEY_EVENT_START = "start";
	public static final String KEY_EVENT_DURATION = "duration";
	public static final String KEY_EVENT_TITLE = "title";
	public static final String KEY_EVENT_DESCRIPTION = "description";
	public static final String KEY_EVENT_DESCRIPTION_EXTENDED = "description_ext";
	public static final String KEY_EVENT_SERVICE_REFERENCE = "sid";
	public static final String KEY_SERVICES_REFERENCE = "ref";
	public static final String KEY_SERVICES_NAME = "name";

	public static final String DATABASE_NAME = "dreamdroid";
	private static final int DATABASE_VERSION = 11;
	private static final String PROFILES_TABLE_NAME = "profiles";
	private static final String EVENT_TABLE_NAME = "events";
	private static final String SERVICES_TABLE_NAME = "services";

	private static final String PROFILES_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " +
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
			KEY_PROFILE_STREAM_LOGIN + " BOOLEAN);";

	private static final String PROFILES_TABLE_UPGRADE_2_3 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_PROFILE_STREAM_HOST + " TEXT;";

	private static final String PROFILES_TABLE_UPGRADE_3_4 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_PROFILE_SIMPLE_REMOTE + " BOOLEAN;";

	private static final String PROFILES_TABLE_UPGRADE_4_5 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_PROFILE_STREAM_PORT + " INTEGER;";

	private static final String PROFILES_TABLE_UPGRADE_5_6 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_PROFILE_FILE_PORT + " INTEGER;";

	private static final String PROFILES_TABLE_UPGRADE_6_7_1 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_PROFILE_DEFAULT_REF + " TEXT;";
	private static final String PROFILES_TABLE_UPGRADE_6_7_2 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_PROFILE_DEFAULT_REF_NAME + " TEXT;";
	private static final String PROFILES_TABLE_UPGRADE_6_7_3 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_PROFILE_DEFAULT_REF_2 + " TEXT;";
	private static final String PROFILES_TABLE_UPGRADE_6_7_4 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_PROFILE_DEFAULT_REF_2_NAME + " TEXT;";

	private static final String PROFILES_TABLE_UPGRADE_7_8_1 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_PROFILE_FILE_LOGIN + " BOOLEAN;";
	private static final String PROFILES_TABLE_UPGRADE_7_8_2 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_PROFILE_FILE_SSL + " BOOLEAN;";

	private static final String PROFILES_TABLE_UPGRADE_8_9 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_PROFILE_STREAM_LOGIN + " BOOLEAN;";

	private static final String EVENT_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " +
			EVENT_TABLE_NAME + " (" +
			KEY_EVENT_ID + " INTEGER PRIMARY KEY, " +
			KEY_EVENT_START + " INTEGER, " +
			KEY_EVENT_DURATION + " INTEGER, " +
			KEY_EVENT_TITLE + " TEXT, " +
			KEY_EVENT_DESCRIPTION + " TEXT, " +
			KEY_EVENT_DESCRIPTION_EXTENDED + " TEXT, " +
            KEY_EVENT_SERVICE_REFERENCE + " TEXT);";

	private static final String SERVICES_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " +
			SERVICES_TABLE_NAME + " (" +
			KEY_SERVICES_REFERENCE + " TEXT PRIMARY KEY, " +
			KEY_SERVICES_NAME + " TEXT);";

	private Context mContext;

	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		mContext = context;
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(PROFILES_TABLE_CREATE);
		db.execSQL(EVENT_TABLE_CREATE);
		db.execSQL(SERVICES_TABLE_CREATE);
	}

	private void upgrade6to7(SQLiteDatabase db){
		db.execSQL(PROFILES_TABLE_UPGRADE_6_7_1);
		db.execSQL(PROFILES_TABLE_UPGRADE_6_7_2);
		db.execSQL(PROFILES_TABLE_UPGRADE_6_7_3);
		db.execSQL(PROFILES_TABLE_UPGRADE_6_7_4);
	}

	private void upgrade7to8(SQLiteDatabase db){
		db.execSQL(PROFILES_TABLE_UPGRADE_7_8_1);
		db.execSQL(PROFILES_TABLE_UPGRADE_7_8_2);
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		boolean scheduleBackup = oldVersion < newVersion;
		try {
			if (oldVersion == 2) {
				db.execSQL(PROFILES_TABLE_UPGRADE_2_3);
				oldVersion++;
			}
			if (oldVersion == 3) {
				db.execSQL(PROFILES_TABLE_UPGRADE_3_4);
				oldVersion++;
			}
			if (oldVersion == 4){
				db.execSQL(PROFILES_TABLE_UPGRADE_4_5);
				oldVersion++;
			}
			if (oldVersion == 5){
				db.execSQL(PROFILES_TABLE_UPGRADE_5_6);
				oldVersion++;
			}
			if(oldVersion == 6){
				upgrade6to7(db);
				oldVersion++;
			}
			if (oldVersion == 7){
				upgrade7to8(db);
				oldVersion++;
			}
			if (oldVersion == 8){
				db.execSQL(PROFILES_TABLE_UPGRADE_8_9);
				oldVersion++;
			}
			if (oldVersion == 9){
				db.execSQL(EVENT_TABLE_CREATE);
				db.execSQL(SERVICES_TABLE_CREATE);
				oldVersion += 2;
			}
            if( oldVersion == 10){ //DEVELOPMENT VERSIONS ONLY
                db.execSQL("DROP TABLE EPG;");
                db.execSQL(EVENT_TABLE_CREATE);
                oldVersion++;
            }
			if (oldVersion != DATABASE_VERSION){ //this should never happen...
				emergencyRecovery(db);
			}


		} catch (SQLiteException e) {
			Log.e(LOG_TAG, "onUpgrade: SQLiteException, recreating db. ", e);
			Log.e(LOG_TAG, "(oldVersion was " + oldVersion + ")");
			emergencyRecovery(db);
			return; // this was lossy
		}
		if(scheduleBackup)
			DreamDroid.scheduleBackup(mContext);
	}

	private void emergencyRecovery(SQLiteDatabase db){
		db.execSQL("DROP TABLE IF EXISTS " + PROFILES_TABLE_NAME + ";");
		db.execSQL(PROFILES_TABLE_CREATE);
	}

	private ContentValues p2cv(Profile p){
		ContentValues values = new ContentValues();
		values.put(KEY_PROFILE_PROFILE, p.getName());
		values.put(KEY_PROFILE_HOST, p.getHost());
		values.put(KEY_PROFILE_STREAM_HOST, p.getStreamHostValue());
		values.put(KEY_PROFILE_PORT, p.getPort());
		values.put(KEY_PROFILE_STREAM_PORT, p.getStreamPort());
		values.put(KEY_PROFILE_FILE_PORT, p.getFilePort());
		values.put(KEY_PROFILE_LOGIN, p.isLogin());
		values.put(KEY_PROFILE_USER, p.getUser());
		values.put(KEY_PROFILE_PASS, p.getPass());
		values.put(KEY_PROFILE_SSL, p.isSsl());
		values.put(KEY_PROFILE_STREAM_LOGIN, p.isStreamLogin());
		values.put(KEY_PROFILE_FILE_LOGIN, p.isFileLogin());
		values.put(KEY_PROFILE_FILE_SSL, p.isFileSsl());
		values.put(KEY_PROFILE_SIMPLE_REMOTE, p.isSimpleRemote());
		values.put(KEY_PROFILE_DEFAULT_REF, p.getDefaultRef());
		values.put(KEY_PROFILE_DEFAULT_REF_NAME, p.getDefaultRefName());
		values.put(KEY_PROFILE_DEFAULT_REF_2, p.getDefaultRef2());
		values.put(KEY_PROFILE_DEFAULT_REF_2_NAME, p.getDefaultRef2Name());
		return values;
	}

	/**
	 * @param p
	 */
	public boolean addProfile(Profile p) {
		SQLiteDatabase db = getWritableDatabase();
		if (db.insert(PROFILES_TABLE_NAME, null, p2cv(p)) > -1) {
			db.close();
			DreamDroid.scheduleBackup(mContext);
			return true;
		}
		db.close();
		return false;
	}

	/**
	 * @param p
	 */
	public boolean updateProfile(Profile p) {
		SQLiteDatabase db = getWritableDatabase();
		int numRows = db.update(PROFILES_TABLE_NAME, p2cv(p), KEY_PROFILE_ID + "=" + p.getId(), null);
		db.close();
		if (numRows == 1) {
			DreamDroid.scheduleBackup(mContext);
			DreamDroid.profileChanged(mContext, p);
			return true;
		}
		return false;
	}

	/**
	 * @param p
	 */
	public boolean deleteProfile(Profile p) {
		SQLiteDatabase db = getWritableDatabase();
		int numRows = db.delete(PROFILES_TABLE_NAME, KEY_PROFILE_ID + "=" + p.getId(), null);
		db.close();
		if (numRows == 1) {
			DreamDroid.scheduleBackup(mContext);
			return true;
		}

		return false;
	}

	/**
	 * @return Profile for all Settings
	 */
	public ArrayList<Profile> getProfiles() {
		String[] columns = {KEY_PROFILE_ID, KEY_PROFILE_PROFILE, KEY_PROFILE_HOST, KEY_PROFILE_STREAM_HOST, KEY_PROFILE_PORT, KEY_PROFILE_STREAM_PORT, KEY_PROFILE_FILE_PORT, KEY_PROFILE_LOGIN, KEY_PROFILE_USER, KEY_PROFILE_PASS,
				KEY_PROFILE_SSL, KEY_PROFILE_SIMPLE_REMOTE, KEY_PROFILE_STREAM_LOGIN, KEY_PROFILE_FILE_LOGIN, KEY_PROFILE_FILE_SSL, KEY_PROFILE_DEFAULT_REF, KEY_PROFILE_DEFAULT_REF_NAME, KEY_PROFILE_DEFAULT_REF_2, KEY_PROFILE_DEFAULT_REF_2_NAME};
		SQLiteDatabase db = getReadableDatabase();

		ArrayList<Profile> list = new ArrayList<>();

		Cursor c = db.query(PROFILES_TABLE_NAME, columns, null, null, null, null, KEY_PROFILE_PROFILE);
		if(c.getCount() == 0){
			db.close();
			c.close();
			return list;
		}

		while(!c.isLast()){
			c.moveToNext();
			list.add(getProfileFrom(c));
		}
		c.close();
		db.close();
		return list;
	}

	/**
	 *
	 * @param id
	 * @return the profile for the given id, or null if no such profile was found
	 */
	public Profile getProfile(int id) {
		String[] columns = {KEY_PROFILE_ID, KEY_PROFILE_PROFILE, KEY_PROFILE_HOST, KEY_PROFILE_STREAM_HOST, KEY_PROFILE_PORT, KEY_PROFILE_STREAM_PORT, KEY_PROFILE_FILE_PORT, KEY_PROFILE_LOGIN, KEY_PROFILE_USER, KEY_PROFILE_PASS,
				KEY_PROFILE_SSL, KEY_PROFILE_SIMPLE_REMOTE, KEY_PROFILE_STREAM_LOGIN, KEY_PROFILE_FILE_LOGIN, KEY_PROFILE_FILE_SSL, KEY_PROFILE_DEFAULT_REF, KEY_PROFILE_DEFAULT_REF_NAME, KEY_PROFILE_DEFAULT_REF_2, KEY_PROFILE_DEFAULT_REF_2_NAME};
		SQLiteDatabase db = getReadableDatabase();
		Cursor c = db.query(PROFILES_TABLE_NAME, columns, KEY_PROFILE_ID + "=" + id, null, null, null, KEY_PROFILE_PROFILE);

		Profile p = null;
		if(c.getCount() == 1 && c.moveToFirst()) {
			p = getProfileFrom(c);
		}
		c.close();
		db.close();
		return p;
	}

	private Profile getProfileFrom(Cursor c) {

		int id = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_ID));
		String name = c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_PROFILE));
		String host = c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_HOST));
		String streamHost = c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_STREAM_HOST));
		int port = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_PORT));

		int streamPort = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_STREAM_PORT));
		if (streamPort <= 0) {
			streamPort = 8001;
		}

		int filePort = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_FILE_PORT));
		if (filePort <= 0) {
			filePort = 80;
		}

		int login = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_LOGIN));
		boolean ynLogin = login == 1;

		int ssl = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_SSL));
		boolean ynSsl = ssl == 1;


		int streamLogin = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_STREAM_LOGIN));
		boolean ynStreamLogin = streamLogin == 1;

		int fileLogin = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_FILE_LOGIN));
		boolean ynFileLogin = fileLogin == 1;

		int fileSsl = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_FILE_SSL));
		boolean ynFileSsl = fileSsl == 1;

		int simpleRemote = c.getInt(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_SIMPLE_REMOTE));
		boolean ynSimpleRemote = simpleRemote == 1;

		String user = c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_USER));
		String pass = c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_PASS));

		String defaultRef = c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_DEFAULT_REF));
		String defaultRefName = c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_DEFAULT_REF_NAME));

		String defaultRef2 = c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_DEFAULT_REF_2));
		String defaultRef2Name = c.getString(c.getColumnIndex(DatabaseHelper.KEY_PROFILE_DEFAULT_REF_2_NAME));

		return new Profile(id, name, host, streamHost, port, streamPort, filePort, ynLogin, user, pass, ynSsl, ynStreamLogin, ynFileLogin, ynFileSsl, ynSimpleRemote, defaultRef, defaultRefName, defaultRef2, defaultRef2Name);
	}

    public int setEvents(ArrayList<ExtendedHashMap> events){
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        int success = 0;
        for(ExtendedHashMap event: events){
            if(setEvent(event, db))
                success++;
        }
        db.endTransaction();
        db.close();
        return success;
    }

    public boolean setEvent(ExtendedHashMap event, SQLiteDatabase db){
        ContentValues values = eventToCv(event);
        if(values == null) {
            return false;
        }

        String id = values.getAsString(KEY_EVENT_ID);
        db.delete(EVENT_TABLE_NAME, KEY_EVENT_ID + "=?;", new String[]{id,});
	    return db.insert(EVENT_TABLE_NAME, null, values) > -1;
    }

    public ContentValues eventToCv(ExtendedHashMap event){
        ContentValues values = new ContentValues();
        int _id, _start, _duration;
        try {
            _id = Integer.parseInt(event.getString(Event.KEY_EVENT_ID));
            _start = Integer.parseInt(event.getString(Event.KEY_EVENT_START));
            _duration = Integer.parseInt(event.getString(Event.KEY_EVENT_DURATION));
        } catch(NumberFormatException nex){
            return null;
        }

        values.put(KEY_EVENT_ID, _id);
        values.put(KEY_EVENT_START, _start);
        values.put(KEY_EVENT_DURATION, _duration);
        values.put(KEY_EVENT_TITLE, event.getString(Event.KEY_EVENT_TITLE));
        values.put(KEY_EVENT_DESCRIPTION,event.getString(Event.KEY_EVENT_DESCRIPTION));
        values.put(KEY_EVENT_DESCRIPTION_EXTENDED,event.getString(Event.KEY_EVENT_DESCRIPTION_EXTENDED));
        values.put(KEY_EVENT_SERVICE_REFERENCE, event.getString(Event.KEY_SERVICE_REFERENCE));
        return values;
    }

    public static DatabaseHelper getInstance(Context ctx){
        return new DatabaseHelper(ctx);
    }

    public boolean exportDB(){
        File sd = Environment.getExternalStorageDirectory();
        File data = Environment.getDataDirectory();
        FileChannel source=null;
        FileChannel destination=null;
        String currentDBPath = "/data/net.reichholf.dreamdroid" +"/databases/" + DATABASE_NAME;
        String backupDBPath = DATABASE_NAME + ".sqlite";
        File currentDB = new File(data, currentDBPath);
        File backupDB = new File(sd, backupDBPath);
        try {
            source = new FileInputStream(currentDB).getChannel();
            destination = new FileOutputStream(backupDB).getChannel();
            destination.transferFrom(source, 0, source.size());
            source.close();
            destination.close();
            return true;
        } catch(IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
