package net.reichholf.dreamdroid.multiepg

/**
 * Two MultiEPG type densities. Compact is the original 36.dp / labelSmall grid.
 * Comfortable is the default: larger titles, taller rows, bigger timer clocks.
 * Row and clock sizes grow with [fontScale] so system font scale does not clip.
 */
enum class MultiEpgTextSize(
    val prefValue: String,
    private val rowHeightDp: Float,
    private val clockDp: Float,
    val channelWidthDp: Float
) {
    Compact("compact", 36f, 12f, 100f),
    Comfortable("comfortable", 48f, 16f, 112f)
    ;

    fun rowHeightDp(fontScale: Float): Float = rowHeightDp * fontScale.coerceAtLeast(1f)

    fun clockSizeDp(fontScale: Float): Float = clockDp * fontScale.coerceAtLeast(1f)

    companion object {
        val DEFAULT: MultiEpgTextSize = Comfortable

        fun fromPref(value: String?): MultiEpgTextSize {
            if (value.isNullOrEmpty()) {
                return DEFAULT
            }
            for (option in entries) {
                if (option.prefValue == value) {
                    return option
                }
            }
            return DEFAULT
        }
    }
}
