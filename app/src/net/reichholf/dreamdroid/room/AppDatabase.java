package net.reichholf.dreamdroid.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import net.reichholf.dreamdroid.Profile;

@Database(entities = {Profile.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
	public abstract Profile.ProfileDao profileDao();

	private static AppDatabase db = null;

	public static AppDatabase getInstance(Context context) {
		if (db != null)
			return db;
		db = Room.databaseBuilder(
						context.getApplicationContext(),
						AppDatabase.class, "dreamdroid"
				)
				.allowMainThreadQueries()
				.build();
		return db;
	}


}
