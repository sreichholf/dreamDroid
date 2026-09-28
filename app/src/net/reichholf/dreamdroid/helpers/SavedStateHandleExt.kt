package net.reichholf.dreamdroid.helpers

import androidx.lifecycle.SavedStateHandle

/** Stores [value], or removes [key] when it is null so a later read sees it as absent. */
fun <T : Any> SavedStateHandle.setOrRemove(key: String, value: T?) {
    if (value == null) {
        remove<T>(key)
    } else {
        set(key, value)
    }
}
