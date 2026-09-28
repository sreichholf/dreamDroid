package net.reichholf.dreamdroid.testutil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job

/**
 * Cancels everything this ViewModel launched (text-field observers never finish on their
 * own) and waits for it. A cancelled load still resumes on Main once its blocking read
 * returns, so tests call this before `Dispatchers.resetMain()`.
 */
suspend fun ViewModel.cancelAndJoin() {
    viewModelScope.coroutineContext.job.cancelAndJoin()
}
