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
	public static final String KEY_DEFAULT_REF = "default_ref";
	public static final String KEY_DEFAULT_REF_NAME = "default_ref_name";
	public static final String KEY_DEFAULT_REF_2 = "default_ref_2";
	public static final String KEY_DEFAULT_REF_2_NAME = "default_ref_2_name";

	public static final String DATABASE_NAME = "dreamdroid";
	private static final int DATABASE_VERSION = 7;
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
				KEY_SIMPLE_REMOTE + " BOOLEAN, " +
				KEY_DEFAULT_REF + " TEXT, " + 
				KEY_DEFAULT_REF_NAME + " TEXT, " +
				KEY_DEFAULT_REF_2 + " TEXT, " +
				KEY_DEFAULT_REF_2_NAME + " TEXT );";

	private static final String PROFILES_TABLE_UPGRADE_2_3 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_STREAM_HOST + " TEXT;";

	private static final String PROFILES_TABLE_UPGRADE_3_4 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_SIMPLE_REMOTE + " BOOLEAN;";

	private static final String PROFILES_TABLE_UPGRADE_4_5 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_STREAM_PORT + " INTEGER;";
	
	private static final String PROFILES_TABLE_UPGRADE_5_6 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD "
			+ KEY_FILE_PORT + " INTEGER;";
	
	private static final String PROFILES_TABLE_UPGRADE_6_7_1 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_DEFAULT_REF + " TEXT;";
	private static final String PROFILES_TABLE_UPGRADE_6_7_2 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_DEFAULT_REF_NAME + " TEXT;";
	private static final String PROFILES_TABLE_UPGRADE_6_7_3 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_DEFAULT_REF_2 + " TEXT;";
	private static final String PROFILES_TABLE_UPGRADE_6_7_4 = "ALTER TABLE " + PROFILES_TABLE_NAME + " ADD " + KEY_DEFAULT_REF_2_NAME + " TEXT;";

	
	private Context mContext;
	/**
	 * @param context
	 * @param name
	 * @param factory
	 * @param version
	 */
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
	}
	
	private void upgrade6to7(SQLiteDatabase db){
		db.execSQL(PROFILES_TABLE_UPGRADE_6_7_1);
		db.execSQL(PROFILES_TABLE_UPGRADE_6_7_2);
		db.execSQL(PROFILES_TABLE_UPGRADE_6_7_3);
		db.execSQL(PROFILES_TABLE_UPGRADE_6_7_4);
	}
	
	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		if (oldVersion == 2) {
			db.execSQL(PROFILES_TABLE_UPGRADE_2_3);
			db.execSQL(PROFILES_TABLE_UPGRADE_3_4);
			db.execSQL(PROFILES_TABLE_UPGRADE_4_5);
			db.execSQL(PROFILES_TABLE_UPGRADE_5_6);
			upgrade6to7(db);
			db.setVersion(DATABASE_VERSION);
		} else if (oldVersion == 3) {
			db.execSQL(PROFILES_TABLE_UPGRADE_3_4);
			db.execSQL(PROFILES_TABLE_UPGRADE_4_5);
			db.execSQL(PROFILES_TABLE_UPGRADE_5_6);
			upgrade6to7(db);
			db.setVersion(DATABASE_VERSION);
		} else if (oldVersion == 4){
			db.execSQL(PROFILES_TABLE_UPGRADE_4_5);
			db.execSQL(PROFILES_TABLE_UPGRADE_5_6);
			upgrade6to7(db);
			db.setVersion(DATABASE_VERSION);
		} else if (oldVersion == 5){
			db.execSQL(PROFILES_TABLE_UPGRADE_5_6);
			upgrade6to7(db);
			db.setVersion(DATABASE_VERSION);
		} else if(oldVersion == 6){
			upgrade6to7(db);
			db.setVersion(DATABASE_VERSION);
		} else {
			db.execSQL("DROP TABLE IF EXISTS " + PROFILES_TABLE_NAME);
			db.setVersion(0);
		}
		if(oldVersion < newVersion)
			DreamDroid.scheduleBackup(mContext);
	}
	
	private ContentValues p2cv(Profile p){
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
		values.put(KEY_DEFAULT_REF, p.getDefaultRef());
		values.put(KEY_DEFAULT_REF_NAME, p.getDefaultRefName());
		values.put(KEY_DEFAULT_REF_2, p.getDefaultRef2());
		values.put(KEY_DEFAULT_REF_2_NAME, p.getDefaultRef2Name());
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
		int numRows = db.update(PROFILES_TABLE_NAME, p2cv(p), KEY_ID + "=" + p.getId(), null);
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
		int numRows = db.delete(PROFILES_TABLE_NAME, KEY_ID + "=" + p.getId(), null);
		db.close();
		if (numRows == 1) {
			DreamDroid.scheduleBackup(mContext);
			return true;
		}

		return false;
	}

	/**
	 * @return Cursor for all Settings
	 */
	public ArrayList<Profile> getProfiles() {
		String[] columns = { KEY_ID, KEY_PROFILE, KEY_HOST, KEY_STREAM_HOST, KEY_PORT, KEY_STREAM_PORT, KEY_FILE_PORT, KEY_LOGIN, KEY_USER, KEY_PASS,
				KEY_SSL, KEY_SIMPLE_REMOTE, KEY_DEFAULT_REF, KEY_DEFAULT_REF_NAME, KEY_DEFAULT_REF_2, KEY_DEFAULT_REF_2_NAME };
		SQLiteDatabase db = getReadableDatabase();

		ArrayList<Profile> list = new ArrayList<Profile>();
		
		Cursor c = db.query(PROFILES_TABLE_NAME, columns, null, null, null, null, KEY_PROFILE);
		if(c.getCount() == 0){
			db.close();
			c.close();
			return list;
		}
		
		while(!c.isLast()){
			c.moveToNext();
			list.add(new Profile(c));
		}
		c.close();
		db.close();
		return list;
	}

	public Profile getProfile(int id) {
		String[] columns = { KEY_ID, KEY_PROFILE, KEY_HOST, KEY_STREAM_HOST, KEY_PORT, KEY_STREAM_PORT, KEY_FILE_PORT, KEY_LOGIN, KEY_USER, KEY_PASS,
				KEY_SSL, KEY_SIMPLE_REMOTE, KEY_DEFAULT_REF, KEY_DEFAULT_REF_NAME, KEY_DEFAULT_REF_2, KEY_DEFAULT_REF_2_NAME };
		SQLiteDatabase db = getReadableDatabase();
		Cursor c = db.query(PROFILES_TABLE_NAME, columns, KEY_ID + "=" + id, null, null, null, KEY_PROFILE);

		Profile p = new Profile();
		if(c.getCount() == 1 && c.moveToFirst())
			p.set(c);
		c.close();
		db.close();
		return p;
	}

	
	public static DatabaseHelper getInstance(Context ctx){
		return new DatabaseHelper(ctx);
	}
}
