package net.reichholf.dreamdroid.ui.zap

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics

/**
 * Bouquet-picker decisions for [ZapDestination]. Extracted so cancel vs reload
 * can be unit-tested without hosting [ZapSession].
 */
object ZapPickerGate {
    data class NonOkEffect(
        val waitingForPicker: Boolean,
        val emptyMessageResId: Int?,
    )

    fun isBouquetPickerRequest(requestCode: Int): Boolean {
        return requestCode == Statics.REQUEST_PICK_BOUQUET
    }

    fun afterNonOkPickerResult(gridEmpty: Boolean): NonOkEffect {
        return NonOkEffect(
            waitingForPicker = false,
            emptyMessageResId = if (gridEmpty) R.string.no_list_item else null,
        )
    }

    fun shouldNavigateToPickBouquet(
        bouquetRef: String,
        waitingForPicker: Boolean,
    ): Boolean {
        return bouquetRef.isEmpty() && !waitingForPicker
    }
}
