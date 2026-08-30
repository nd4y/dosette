package icu.nd4y.dosette.reminders

/**
 * Lets the engine push fresh data to the home-screen widget without
 * depending on the widget implementation.
 */
fun interface WidgetRefresher {
    suspend fun refresh()
}
