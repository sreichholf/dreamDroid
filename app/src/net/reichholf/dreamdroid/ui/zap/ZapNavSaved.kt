package net.reichholf.dreamdroid.ui.zap

object ZapNavSavedKeys {
    const val BOUQUET_REF = "zap_bouquet_ref"
    const val BOUQUET_NAME = "zap_bouquet_name"
    const val WAITING_FOR_PICKER = "zap_waiting_for_picker"
}

data class ZapNavSaved(
    val bouquetRef: String,
    val bouquetName: String,
    val waitingForPicker: Boolean
)

interface ZapNavSavedAccess {
    fun getBouquetRef(): String?
    fun setBouquetRef(bouquetRef: String)
    fun getBouquetName(): String?
    fun setBouquetName(bouquetName: String)
    fun getWaitingForPicker(): Boolean?
    fun setWaitingForPicker(waitingForPicker: Boolean)
}

class MapZapNavSavedAccess(private val values: MutableMap<String, Any> = mutableMapOf()) :
    ZapNavSavedAccess {
    override fun getBouquetRef(): String? = values[ZapNavSavedKeys.BOUQUET_REF] as? String

    override fun setBouquetRef(bouquetRef: String) {
        values[ZapNavSavedKeys.BOUQUET_REF] = bouquetRef
    }

    override fun getBouquetName(): String? = values[ZapNavSavedKeys.BOUQUET_NAME] as? String

    override fun setBouquetName(bouquetName: String) {
        values[ZapNavSavedKeys.BOUQUET_NAME] = bouquetName
    }

    override fun getWaitingForPicker(): Boolean? =
        values[ZapNavSavedKeys.WAITING_FOR_PICKER] as? Boolean

    override fun setWaitingForPicker(waitingForPicker: Boolean) {
        values[ZapNavSavedKeys.WAITING_FOR_PICKER] = waitingForPicker
    }
}

/**
 * Absent strings resolve to the profile defaults. Absent waiting reads as false.
 * Reading does not write those defaults back.
 */
fun readZapNavSaved(
    access: ZapNavSavedAccess,
    defaultBouquetRef: String,
    defaultBouquetName: String
): ZapNavSaved = ZapNavSaved(
    bouquetRef = access.getBouquetRef() ?: defaultBouquetRef,
    bouquetName = access.getBouquetName() ?: defaultBouquetName,
    waitingForPicker = access.getWaitingForPicker() ?: false
)

fun ZapNavSaved.writeTo(access: ZapNavSavedAccess) {
    access.setBouquetRef(bouquetRef)
    access.setBouquetName(bouquetName)
    access.setWaitingForPicker(waitingForPicker)
}
