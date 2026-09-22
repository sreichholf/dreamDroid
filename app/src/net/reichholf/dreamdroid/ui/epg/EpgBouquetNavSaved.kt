package net.reichholf.dreamdroid.ui.epg

object EpgBouquetNavSavedKeys {
    const val BOUQUET_REF = "epg_bouquet_ref"
    const val BOUQUET_NAME = "epg_bouquet_name"
    const val TIME_SEC = "epg_bouquet_time_sec"
    const val WAITING_FOR_PICKER = "epg_bouquet_waiting_for_picker"
}

data class EpgBouquetNavSaved(
    val bouquetRef: String,
    val bouquetName: String,
    val timeSec: Long?,
    val waitingForPicker: Boolean
)

interface EpgBouquetNavSavedAccess {
    fun getBouquetRef(): String?
    fun setBouquetRef(bouquetRef: String)
    fun getBouquetName(): String?
    fun setBouquetName(bouquetName: String)
    fun getTimeSec(): Long?
    fun setTimeSec(timeSec: Long?)
    fun getWaitingForPicker(): Boolean?
    fun setWaitingForPicker(waitingForPicker: Boolean)
}

class MapEpgBouquetNavSavedAccess(private val values: MutableMap<String, Any> = mutableMapOf()) :
    EpgBouquetNavSavedAccess {
    override fun getBouquetRef(): String? = values[EpgBouquetNavSavedKeys.BOUQUET_REF] as? String

    override fun setBouquetRef(bouquetRef: String) {
        values[EpgBouquetNavSavedKeys.BOUQUET_REF] = bouquetRef
    }

    override fun getBouquetName(): String? = values[EpgBouquetNavSavedKeys.BOUQUET_NAME] as? String

    override fun setBouquetName(bouquetName: String) {
        values[EpgBouquetNavSavedKeys.BOUQUET_NAME] = bouquetName
    }

    override fun getTimeSec(): Long? = values[EpgBouquetNavSavedKeys.TIME_SEC] as? Long

    override fun setTimeSec(timeSec: Long?) {
        if (timeSec == null) {
            values.remove(EpgBouquetNavSavedKeys.TIME_SEC)
        } else {
            values[EpgBouquetNavSavedKeys.TIME_SEC] = timeSec
        }
    }

    override fun getWaitingForPicker(): Boolean? =
        values[EpgBouquetNavSavedKeys.WAITING_FOR_PICKER] as? Boolean

    override fun setWaitingForPicker(waitingForPicker: Boolean) {
        values[EpgBouquetNavSavedKeys.WAITING_FOR_PICKER] = waitingForPicker
    }
}

/**
 * Absent strings read as empty. Absent [EpgBouquetNavSaved.timeSec] stays null, including when
 * the missing value would otherwise be 0. Absent waiting reads as false. Reading does not write.
 * A null time write removes the key. Waiting false is stored after a write.
 */
fun readEpgBouquetNavSaved(access: EpgBouquetNavSavedAccess): EpgBouquetNavSaved =
    EpgBouquetNavSaved(
        bouquetRef = access.getBouquetRef().orEmpty(),
        bouquetName = access.getBouquetName().orEmpty(),
        timeSec = access.getTimeSec(),
        waitingForPicker = access.getWaitingForPicker() ?: false
    )

fun EpgBouquetNavSaved.writeTo(access: EpgBouquetNavSavedAccess) {
    access.setBouquetRef(bouquetRef)
    access.setBouquetName(bouquetName)
    access.setTimeSec(timeSec)
    access.setWaitingForPicker(waitingForPicker)
}
