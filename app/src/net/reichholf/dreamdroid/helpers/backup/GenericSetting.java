package net.reichholf.dreamdroid.helpers.backup;

/**
 * Created by GAigner on 01/09/18.
 */
public class GenericSetting {

    private final String mKey;
    private final String mValue;
    private final String mType;

    public GenericSetting(String key, String value, String type) {
        mKey = key;
        mValue = value;
        mType = type;
    }

    public String getKey() {
        return mKey;
    }

    public String getType() {
        return mType;
    }

    public String getValue() {
        return mValue;
    }
}
