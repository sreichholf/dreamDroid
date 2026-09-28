package net.reichholf.dreamdroid.ui.epg

import androidx.lifecycle.SavedStateHandle
import net.reichholf.dreamdroid.helpers.setOrRemove

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

/**
 * Absent strings read as empty. Absent [EpgBouquetNavSaved.timeSec] stays null, including when
 * the missing value would otherwise be 0. Absent waiting reads as false. Reading does not write.
 * A null time write removes the key. Waiting false is stored after a write.
 */
fun readEpgBouquetNavSaved(handle: SavedStateHandle): EpgBouquetNavSaved = EpgBouquetNavSaved(
    bouquetRef = handle.get<String>(EpgBouquetNavSavedKeys.BOUQUET_REF).orEmpty(),
    bouquetName = handle.get<String>(EpgBouquetNavSavedKeys.BOUQUET_NAME).orEmpty(),
    timeSec = handle.get<Long>(EpgBouquetNavSavedKeys.TIME_SEC),
    waitingForPicker = handle.get<Boolean>(EpgBouquetNavSavedKeys.WAITING_FOR_PICKER) ?: false
)

fun EpgBouquetNavSaved.writeTo(handle: SavedStateHandle) {
    handle[EpgBouquetNavSavedKeys.BOUQUET_REF] = bouquetRef
    handle[EpgBouquetNavSavedKeys.BOUQUET_NAME] = bouquetName
    handle.setOrRemove(EpgBouquetNavSavedKeys.TIME_SEC, timeSec)
    handle[EpgBouquetNavSavedKeys.WAITING_FOR_PICKER] = waitingForPicker
}
