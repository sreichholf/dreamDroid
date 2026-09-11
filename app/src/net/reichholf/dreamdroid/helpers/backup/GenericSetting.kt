package net.reichholf.dreamdroid.helpers.backup

class GenericSetting(
    private val mKey: String,
    private val mValue: String,
    private val mType: String,
) {
    fun getKey(): String = mKey
    fun getType(): String = mType
    fun getValue(): String = mValue
}
