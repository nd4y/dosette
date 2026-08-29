package icu.nd4y.dosette.ui

import com.github.takahirom.roborazzi.RoborazziOptions

/**
 * Tolerance for golden comparison in CI: font anti-aliasing differs between
 * the OS the goldens were recorded on and the CI runner, so exact-pixel
 * comparison would flake. 2% of pixels absorbs rendering noise while still
 * failing on real layout or color changes.
 */
val SHOT_OPTIONS =
    RoborazziOptions(
        compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.02f),
    )
