package net.reichholf.dreamdroid.ui.bouqueteditor

import net.reichholf.dreamdroid.R
import net.reichholf.dreamdroid.data.BouquetEditResult
import net.reichholf.dreamdroid.enigma.userMessageText
import net.reichholf.dreamdroid.ui.text.UiText

/** A failed edit's reply; else the result of the backup this edit ran first, if any. */
internal fun BouquetEditResult.userMessage(): UiText? {
    if (!succeeded) {
        return response.userMessageText()
    }
    val backup = backup ?: return null
    val file = backup.value?.stateText
    return if (backup.error == null && !file.isNullOrEmpty()) {
        UiText.Resource(R.string.bouquet_backup_saved, listOf(file))
    } else {
        UiText.Resource(R.string.bouquet_backup_failed)
    }
}
