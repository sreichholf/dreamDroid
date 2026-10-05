package net.reichholf.dreamdroid.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.reichholf.dreamdroid.enigma.EnigmaResponse
import net.reichholf.dreamdroid.helpers.EnigmaHttpError
import net.reichholf.dreamdroid.ui.session.SessionConnectionHolder

/**
 * The cache-first read of one receiver dataset (docs/offline-and-errors.md). Unless
 * [forceRefresh], emits the Room value from [cached] first and stops there while the session
 * is Offline. Otherwise asks the receiver with [fetch]; when that fails, Room is the fallback
 * (also on a forced refresh) before the failure. [loaded] and [failed] build the caller's
 * load type; `cached` is true for a Room value.
 */
internal fun <T : Any, R> cacheFirstLoad(
    sessions: SessionConnectionHolder,
    forceRefresh: Boolean,
    cached: suspend () -> T?,
    fetch: suspend () -> EnigmaResponse<T>,
    loaded: (value: T, cached: Boolean) -> R,
    failed: (EnigmaHttpError?) -> R
): Flow<R> = flow {
    val painted = if (forceRefresh) null else cached()
    if (painted != null) {
        emit(loaded(painted, true))
    }
    if (!forceRefresh && sessions.status.value.shouldSkipReceiverHttp(painted != null)) {
        return@flow
    }
    val response = fetch()
    val live = response.value
    if (live != null) {
        emit(loaded(live, false))
        return@flow
    }
    val fallback = cached()
    emit(if (fallback != null) loaded(fallback, true) else failed(response.error))
}
