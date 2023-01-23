package net.reichholf.dreamdroid.room;

import androidx.room.ProvidedTypeConverter;
import androidx.room.TypeConverter;

public class ProfilesConverter {
	@TypeConverter
	public String StringFromInt(Integer i) {
		return String.valueOf(i);
	}

	@TypeConverter
	public Integer IntFromString(String s) {
		return Integer.valueOf(s);
	}
}