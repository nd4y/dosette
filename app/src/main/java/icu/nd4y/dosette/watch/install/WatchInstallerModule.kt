package icu.nd4y.dosette.watch.install

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchInstallerModule {
    @Binds
    abstract fun installer(implementation: LocalWatchInstaller): WatchInstaller
}
