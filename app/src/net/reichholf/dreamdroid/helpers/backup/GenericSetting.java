package net.reichholf.dreamdroid.helpers.backup;

/**
 * Created by GAigner on 01/09/18.
 */
public class GenericSetting {

    private final String key;
    private final String value;
    private final String type;

    public GenericSetting(String key, String value, String type) {
        this.key = key;
        this.value = value;
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }
}
