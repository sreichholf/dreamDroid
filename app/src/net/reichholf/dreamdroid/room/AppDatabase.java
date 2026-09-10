package net.reichholf.dreamdroid.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import net.reichholf.dreamdroid.Profile;

@Database(entities = {Profile.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
	/** Room profile DB file name under {@code databases/}. */
	public static final String DATABASE_NAME = "dreambox";

	public abstract Profile.ProfileDao profileDao();

	private static AppDatabase db = null;


	public static AppDatabase database(Context context) {
		if (db != null)
			return db;
		db = Room.databaseBuilder(
						context.getApplicationContext(),
						AppDatabase.class, DATABASE_NAME
				)
				.allowMainThreadQueries()
				.build();
		return db;
	}

	public static Profile.ProfileDao profiles(Context context) {
		return database(context).profileDao();
	}


}
