package net.reichholf.dreamdroid.ui.zap

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.helpers.Statics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZapPickerGateTest {
    @Test
    fun cancelClearsWaitingForPickerAndEmptyBouquetReloadReopensPicker() {
        assertFalse(
            ZapPickerGate.shouldNavigateToPickBouquet(
                bouquetRef = "",
                waitingForPicker = true
            )
        )

        val afterCancel = ZapPickerGate.afterNonOkPickerResult(gridEmpty = true)
        assertFalse(afterCancel.waitingForPicker)
        assertEquals(R.string.no_list_item, afterCancel.emptyMessageResId)
        assertTrue(
            ZapPickerGate.shouldNavigateToPickBouquet(
                bouquetRef = "",
                waitingForPicker = afterCancel.waitingForPicker
            )
        )
    }

    @Test
    fun unrelatedRequestCodeIsIgnored() {
        assertTrue(ZapPickerGate.isBouquetPickerRequest(Statics.REQUEST_PICK_BOUQUET))
        assertFalse(ZapPickerGate.isBouquetPickerRequest(Statics.REQUEST_PICK_BOUQUET + 1))
    }

    @Test
    fun cancelWithPopulatedGridDoesNotSetEmptyMessage() {
        val afterCancel = ZapPickerGate.afterNonOkPickerResult(gridEmpty = false)
        assertFalse(afterCancel.waitingForPicker)
        assertNull(afterCancel.emptyMessageResId)
        assertFalse(
            ZapPickerGate.shouldNavigateToPickBouquet(
                bouquetRef = "1:7:1:0:0:0:0:0:0:0:",
                waitingForPicker = afterCancel.waitingForPicker
            )
        )
    }
}
