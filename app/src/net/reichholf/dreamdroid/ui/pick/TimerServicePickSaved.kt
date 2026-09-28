package net.reichholf.dreamdroid.ui.pick

import androidx.lifecycle.SavedStateHandle

object TimerServicePickSavedKeys {
    const val BOUQUET_REF = "timer_service_pick_bouquet_ref"
    const val BOUQUET_NAME = "timer_service_pick_bouquet_name"
}

data class TimerServicePickSaved(val bouquetRef: String = "", val bouquetName: String = "")

/** Absent ref and name read as empty strings. Reading does not write them back. */
fun readTimerServicePickSaved(handle: SavedStateHandle): TimerServicePickSaved =
    TimerServicePickSaved(
        bouquetRef = handle.get<String>(TimerServicePickSavedKeys.BOUQUET_REF).orEmpty(),
        bouquetName = handle.get<String>(TimerServicePickSavedKeys.BOUQUET_NAME).orEmpty()
    )

fun TimerServicePickSaved.writeTo(handle: SavedStateHandle) {
    handle[TimerServicePickSavedKeys.BOUQUET_REF] = bouquetRef
    handle[TimerServicePickSavedKeys.BOUQUET_NAME] = bouquetName
}
