package net.reichholf.dreamdroid.room;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import net.reichholf.dreamdroid.Profile;

@Database(entities = {Profile.class}, version = 15)
@TypeConverters(ProfilesConverter.class)
public abstract class AppDatabase extends RoomDatabase {
	public abstract Profile.ProfileDao profileDao();
	private static AppDatabase db = null;
	static final Migration MIGRATION_14_15 = new Migration(14, 15) {
		@Override
		public void migrate(SupportSQLiteDatabase database) {
			// Empty implementation, because the schema isn't changing.
		}
	};

	public static AppDatabase getInstance(Context context) {
		if (db != null)
			return db;
		db = Room.databaseBuilder(
						context.getApplicationContext(),
						AppDatabase.class, "dreamdroid"
				)
				.addMigrations(MIGRATION_14_15).build();
		return db;
	}



}
