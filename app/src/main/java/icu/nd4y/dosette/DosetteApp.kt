package icu.nd4y.dosette

import android.app.Application
import android.util.Log
import androidx.core.os.UserManagerCompat
import dagger.hilt.android.HiltAndroidApp
import icu.nd4y.dosette.data.ProfileBootstrap
import icu.nd4y.dosette.di.IoDispatcher
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.notifications.Channels
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DosetteApp : Application() {
    @Inject
    lateinit var engine: ReminderEngine

    @Inject
    lateinit var profileBootstrap: ProfileBootstrap

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreate() {
        super.onCreate()
        Channels.ensureCreated(this)
        // Direct boot: a receiver started the process before the first
        // unlock, while the database is still credential-encrypted — opening
        // it would crash. BootReceiver reconciles once the unlock delivers
        // BOOT_COMPLETED.
        if (!UserManagerCompat.isUserUnlocked(this)) return
        // Catch up on anything that became due while the process was dead
        // and make sure the alarm chain is armed.
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            runCatching {
                profileBootstrap.ensureDefaultProfile(getString(R.string.default_profile_name))
                engine.processDueEvents()
            }.onFailure { Log.e("DosetteApp", "startup reconcile failed", it) }
        }
    }
}
