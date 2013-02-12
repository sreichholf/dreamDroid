/* © 2010 Stephan Reichholf <stephan at reichholf dot net>
 * 
 * Licensed under the Create-Commons Attribution-Noncommercial-Share Alike 3.0 Unported
 * http://creativecommons.org/licenses/by-nc-sa/3.0/
 */

package net.reichholf.dreamdroid;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * @author sre
 *
 */
public class DatabaseHelper extends SQLiteOpenHelper {
	public static final String KEY_ID = "_id";
	public static final String KEY_PROFILE = "profile";
	public static final String KEY_HOST = "host";
	public static final String KEY_STREAM_HOST = "streamhost";
	public static final String KEY_STREAM_PORT = "streamport";
	public static final String KEY_FILE_PORT = "fileport";
	public static final String KEY_PORT = "port";
	public static final String KEY_LOGIN = "login";
	public static final String KEY_USER = "user";
	public static final String KEY_PASS = "pass";
	public static final String KEY_SSL = "ssl";
	public static final String KEY_SIMPLE_REMOTE = "simpleremote";
	
	private static DatabaseHelper sInstance = null;
	
	private static final String DATABASE_NAME = "dreamdroid";
	private static final int DATABASE_VERSION = 6;
	private static final String PROFILES_TABLE_NAME = "profiles";

	private static final String PROFILES_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS " + 
				PROFILES_TABLE_NAME + " (" +
				KEY_ID + " INTEGER PRIMARY KEY, " + 
				KEY_PROFILE + " TEXT, " +
				KEY_HOST + " TEXT, " +
				KEY_STREAM_HOST + " TEXT, " + 
				KEY_PORT + " INTEGER, " + 
				KEY_STREAM_PORT + " INTEGER, " + 
				KEY_FILE_PORT + " INTEGER, " +
				KEY_LOGIN + " BOOLEAN, " + 
				KEY_USER + " TEXT, " + 
				KEY_PASS + " TEXT, " + 
				KEY_SSL + " BOOLEAN, " + 
				KEY_SIMPLE_REMOTE + " BOOLEAN );";

	private static final String PROFILES_TABLE_UPGRADE_2_3 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_STREAM_HOST + " TEXT;";

	private static final String PROFILES_TABLE_UPGRADE_3_4 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_SIMPLE_REMOTE + " BOOLEAN;";

	private static final String PROFILES_TABLE_UPGRADE_4_5 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_STREAM_PORT + " INTEGER;";
	
	private static final String PROFILES_TABLE_UPGRADE_5_6 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_FILE_PORT + " INTEGER;";
	/**
	 * @param context
	 * @param name
	 * @param factory
	 * @param version
	 */
	public DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(PROFILES_TABLE_CREATE);
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (db.getVersion() == 2) {
			db.execSQL(PROFILES_TABLE_UPGRADE_2_3);
			db.execSQL(PROFILES_TABLE_UPGRADE_3_4);
			db.execSQL(PROFILES_TABLE_UPGRADE_4_5);
			db.execSQL(PROFILES_TABLE_UPGRADE_5_6);
			db.setVersion(DATABASE_VERSION);
		} else if (db.getVersion() == 3) {
			db.execSQL(PROFILES_TABLE_UPGRADE_3_4);
			db.execSQL(PROFILES_TABLE_UPGRADE_4_5);
			db.execSQL(PROFILES_TABLE_UPGRADE_5_6);
			db.setVersion(DATABASE_VERSION);
		} else if (db.getVersion() == 4){
			db.execSQL(PROFILES_TABLE_UPGRADE_4_5);
			db.execSQL(PROFILES_TABLE_UPGRADE_5_6);
			db.setVersion(DATABASE_VERSION);
		} else if (db.getVersion() == 5){
			db.execSQL(PROFILES_TABLE_UPGRADE_5_6);
			db.setVersion(DATABASE_VERSION);
		} else {
			db.execSQL("DROP TABLE IF EXISTS " + PROFILES_TABLE_NAME);
			db.setVersion(0);
		}
	}
	
	/**
	 * @param p
	 */
	public boolean addProfile(Profile p) {
		ContentValues values = new ContentValues();
		values.put(KEY_PROFILE, p.getName());
		values.put(KEY_HOST, p.getHost());
		values.put(KEY_STREAM_HOST, p.getStreamHostValue());
		values.put(KEY_PORT, p.getPort());
		values.put(KEY_STREAM_PORT, p.getStreamPort());
		values.put(KEY_FILE_PORT, p.getFilePort());
		values.put(KEY_LOGIN, p.isLogin());
		values.put(KEY_USER, p.getUser());
		values.put(KEY_PASS, p.getPass());
		values.put(KEY_SSL, p.isSsl());
		values.put(KEY_SIMPLE_REMOTE, p.isSimpleRemote());
		
		SQLiteDatabase db = getWritableDatabase();
		if (db.insert(PROFILES_TABLE_NAME, null, values) > -1) {
			return true;
		}

		return false;
	}

	/**
	 * @return Cursor for all Settings
	 */
	public ArrayList<Profile> getProfiles() {
		String[] columns = { KEY_ID, KEY_PROFILE, KEY_HOST, KEY_STREAM_HOST, KEY_PORT, KEY_STREAM_PORT, KEY_FILE_PORT, KEY_LOGIN, KEY_USER, KEY_PASS,
				KEY_SSL, KEY_SIMPLE_REMOTE };
		SQLiteDatabase db = getWritableDatabase();
		
		ArrayList<Profile> list = new ArrayList<Profile>();
		
		Cursor c = db.query(PROFILES_TABLE_NAME, columns, null, null, null, null, KEY_PROFILE);
		if(c.getCount() == 0)
			return list;
		
		while(!c.isLast()){
			c.moveToNext();
			list.add(new Profile(c));
		}
		c.close();
		return list;
	}

	public Profile getProfile(int id) {
		String[] columns = { KEY_ID, KEY_PROFILE, KEY_HOST, KEY_STREAM_HOST, KEY_PORT, KEY_STREAM_PORT, KEY_FILE_PORT, KEY_LOGIN, KEY_USER, KEY_PASS,
				KEY_SSL, KEY_SIMPLE_REMOTE };
		SQLiteDatabase db = getReadableDatabase();
		Cursor c = db.query(PROFILES_TABLE_NAME, columns, KEY_ID + "=" + id, null, null, null, KEY_PROFILE);

		Profile p = new Profile();
		if(c.getCount() == 1 && c.moveToFirst())
			p.set(c);
		c.close();
		return p;
	}

	/**
	 * @param p
	 */
	public boolean updateProfile(Profile p) {
		ContentValues values = new ContentValues();
		values.put(KEY_PROFILE, p.getName());
		values.put(KEY_HOST, p.getHost());
		values.put(KEY_STREAM_HOST, p.getStreamHostValue());
		values.put(KEY_PORT, p.getPort());
		values.put(KEY_STREAM_PORT, p.getStreamPort());
		values.put(KEY_FILE_PORT, p.getFilePort());
		values.put(KEY_LOGIN, p.isLogin());
		values.put(KEY_USER, p.getUser());
		values.put(KEY_PASS, p.getPass());
		values.put(KEY_SSL, p.isSsl());
		values.put(KEY_SIMPLE_REMOTE, p.isSimpleRemote());
		
		SQLiteDatabase db = getWritableDatabase();
		int numRows = db.update(PROFILES_TABLE_NAME, values, KEY_ID + "=" + p.getId(), null);

		if (numRows == 1) {
			return true;
		}

		return false;
	}
	
	/**
	 * @param p
	 */
	public boolean deleteProfile(Profile p) {
		SQLiteDatabase db = getWritableDatabase();
		int numRows = db.delete(PROFILES_TABLE_NAME, KEY_ID + "=" + p.getId(), null);
		if (numRows == 1) {
			return true;
		}

		return false;
	}
	
	public static DatabaseHelper getInstance(Context ctx){
		if(sInstance == null)
			sInstance = new DatabaseHelper(ctx);
		return sInstance;
	}
}
