package net.reichholf.dreamdroid.ui.zap

import androidx.lifecycle.SavedStateHandle

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

/**
 * Absent strings resolve to the profile defaults. Absent waiting reads as false.
 * Reading does not write those defaults back.
 */
fun readZapNavSaved(
    handle: SavedStateHandle,
    defaultBouquetRef: String,
    defaultBouquetName: String
): ZapNavSaved = ZapNavSaved(
    bouquetRef = handle.get<String>(ZapNavSavedKeys.BOUQUET_REF) ?: defaultBouquetRef,
    bouquetName = handle.get<String>(ZapNavSavedKeys.BOUQUET_NAME) ?: defaultBouquetName,
    waitingForPicker = handle.get<Boolean>(ZapNavSavedKeys.WAITING_FOR_PICKER) ?: false
)

fun ZapNavSaved.writeTo(handle: SavedStateHandle) {
    handle[ZapNavSavedKeys.BOUQUET_REF] = bouquetRef
    handle[ZapNavSavedKeys.BOUQUET_NAME] = bouquetName
    handle[ZapNavSavedKeys.WAITING_FOR_PICKER] = waitingForPicker
}
