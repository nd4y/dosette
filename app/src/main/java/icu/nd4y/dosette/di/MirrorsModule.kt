package icu.nd4y.dosette.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import icu.nd4y.dosette.reminders.MirrorRefresher
import icu.nd4y.dosette.watch.GmsWatchLink
import icu.nd4y.dosette.watch.WatchLink
import icu.nd4y.dosette.watch.WatchPublisher
import icu.nd4y.dosette.widget.WidgetUpdater
import javax.inject.Singleton

/** Everything that mirrors the day outside the app: the home-screen widget and the watch. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MirrorsModule {
    @Binds
    abstract fun bindWatchLink(impl: GmsWatchLink): WatchLink

    companion object {
        @Provides
        @Singleton
        fun provideMirrorRefresher(
            widget: WidgetUpdater,
            watch: WatchPublisher,
        ): MirrorRefresher =
            MirrorRefresher {
                widget.updateAll()
                watch.publish()
            }
    }
}
