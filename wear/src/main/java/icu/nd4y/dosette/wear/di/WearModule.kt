package icu.nd4y.dosette.wear.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import icu.nd4y.dosette.wear.link.GmsWearLink
import icu.nd4y.dosette.wear.link.WearLink
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WearModule {
    @Binds
    abstract fun bindWearLink(impl: GmsWearLink): WearLink

    companion object {
        // The zone is resolved per call, as in the phone app: a process alive
        // across a timezone change must not keep yesterday's "today".
        @Provides
        @Singleton
        fun provideClock(): Clock =
            object : Clock() {
                override fun getZone(): ZoneId = ZoneId.systemDefault()

                override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

                override fun instant(): Instant = Instant.now()
            }
    }
}
