package net.reichholf.dreamdroid.ui.pick

object TimerServicePickSavedKeys {
    const val BOUQUET_REF = "timer_service_pick_bouquet_ref"
    const val BOUQUET_NAME = "timer_service_pick_bouquet_name"
}

data class TimerServicePickSaved(val bouquetRef: String = "", val bouquetName: String = "")

interface TimerServicePickSavedAccess {
    fun getBouquetRef(): String?

    fun setBouquetRef(bouquetRef: String)

    fun getBouquetName(): String?

    fun setBouquetName(bouquetName: String)
}

class MapTimerServicePickSavedAccess(private val values: MutableMap<String, Any> = mutableMapOf()) :
    TimerServicePickSavedAccess {
    override fun getBouquetRef(): String? = values[TimerServicePickSavedKeys.BOUQUET_REF] as? String

    override fun setBouquetRef(bouquetRef: String) {
        values[TimerServicePickSavedKeys.BOUQUET_REF] = bouquetRef
    }

    override fun getBouquetName(): String? =
        values[TimerServicePickSavedKeys.BOUQUET_NAME] as? String

    override fun setBouquetName(bouquetName: String) {
        values[TimerServicePickSavedKeys.BOUQUET_NAME] = bouquetName
    }
}

/** Absent ref and name read as empty strings. Reading does not write them back. */
fun readTimerServicePickSaved(access: TimerServicePickSavedAccess): TimerServicePickSaved =
    TimerServicePickSaved(
        bouquetRef = access.getBouquetRef().orEmpty(),
        bouquetName = access.getBouquetName().orEmpty()
    )

fun TimerServicePickSaved.writeTo(access: TimerServicePickSavedAccess) {
    access.setBouquetRef(bouquetRef)
    access.setBouquetName(bouquetName)
}
