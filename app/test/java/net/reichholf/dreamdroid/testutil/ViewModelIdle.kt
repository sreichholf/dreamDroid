package net.reichholf.dreamdroid.testutil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll

/**
 * Waits until every coroutine this ViewModel launched has finished, cancelled ones
 * included. A cancelled load still resumes on Main once its blocking read returns, so
 * tests call this before `Dispatchers.resetMain()`.
 */
suspend fun ViewModel.awaitIdle() {
    while (true) {
        val running = viewModelScope.coroutineContext.job.children.toList()
        if (running.isEmpty()) {
            return
        }
        running.joinAll()
    }
}
