package net.reichholf.dreamdroid.helpers

import android.content.Context
import android.content.res.Configuration

/** Android TV and Google TV run in the television UI mode. */
fun Context.isTelevision(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
