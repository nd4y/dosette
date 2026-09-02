package icu.nd4y.dosette.reminders

import android.os.UserManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowUserManager

/**
 * A user locked from process start: the state between a reboot and the
 * first unlock. Installed via `@Config(shadows = ...)` rather than
 * `setUserUnlocked(false)` inside the test so that [icu.nd4y.dosette.DosetteApp]
 * already sees it in onCreate and skips its startup reconcile — the alarm that
 * pass arms from a background thread would otherwise race every assertion
 * on the alarm manager.
 */
@Implements(UserManager::class)
class ShadowLockedUserManager : ShadowUserManager() {
    @Implementation
    override fun isUserUnlocked(): Boolean = false
}
