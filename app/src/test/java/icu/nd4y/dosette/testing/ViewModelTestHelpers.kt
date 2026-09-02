package icu.nd4y.dosette.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll

/**
 * Runs [action] and waits for every coroutine it started in `viewModelScope`
 * to finish, hops through Room's executor included. Coroutines alive before
 * the call — a `stateIn` sharing loop, an init block still loading — are not
 * waited for, so a subscribed UI state does not block the wait. Call inside
 * `runTest` with [MainDispatcherRule] applied.
 */
suspend fun <VM : ViewModel> VM.runAndAwait(action: VM.() -> Unit) {
    val scopeJob = viewModelScope.coroutineContext.job
    val before = scopeJob.children.toSet()
    action()
    scopeJob.children
        .filter { it !in before }
        .toList()
        .joinAll()
}

/** Cancels `viewModelScope` the way the framework does when the owner goes away. */
fun ViewModel.clearForTest() {
    ViewModelStore().apply { put("under-test", this@clearForTest) }.clear()
}
