package icu.nd4y.dosette.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore

    // Injected everywhere time is read: tests substitute a fixed Clock.
    // The zone is resolved per call — a plain systemDefaultZone() would
    // freeze the zone captured at process start, and a process alive across
    // a timezone change would keep computing occurrences in the old zone
    // no matter what TimeChangeReceiver does.
    @Provides
    @Singleton
    fun provideClock(): Clock =
        object : Clock() {
            override fun getZone(): ZoneId = ZoneId.systemDefault()

            override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

            override fun instant(): Instant = Instant.now()
        }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
