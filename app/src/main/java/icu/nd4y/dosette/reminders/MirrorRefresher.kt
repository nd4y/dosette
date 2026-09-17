package icu.nd4y.dosette.reminders

/**
 * Lets the engine push fresh data to everything that mirrors the day
 * outside the app — the home-screen widget and the paired watch — without
 * depending on either implementation.
 */
fun interface MirrorRefresher {
    suspend fun refresh()
}
