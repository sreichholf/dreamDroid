package net.reichholf.dreamdroid.testutil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout

/**
 * Cancels everything this ViewModel launched (text-field observers never finish on their
 * own) and waits for it. A cancelled load still resumes on Main once its blocking read
 * returns, so tests call this before `Dispatchers.resetMain()`.
 */
suspend fun ViewModel.cancelAndJoin() {
    viewModelScope.coroutineContext.job.cancelAndJoin()
}

/** What this ViewModel runs right now: its collectors, and loads and writes in flight. */
fun ViewModel.jobs(): Set<Job> = viewModelScope.coroutineContext.job.children.toSet()

/**
 * Waits for what this ViewModel started since [before], and for what that started in turn,
 * so a test can check that an action sent no request. [before] holds the collectors that
 * never finish; take it from [jobs] right before the action.
 */
suspend fun ViewModel.joinJobsSince(before: Set<Job>) {
    withTimeout(5_000L) {
        while (true) {
            val started = jobs() - before
            if (started.isEmpty()) {
                return@withTimeout
            }
            started.joinAll()
        }
    }
}
