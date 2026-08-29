# Dosette

Offline medication tracker for Android. Material 3 Expressive, no accounts, no network — your data stays on the device.

## Features

- **Medications with flexible schedules**: fixed times, weekdays, every-N-days, cycles (X on / Y off), as-needed.
- **Persistent reminders**: the notification cannot be dismissed for good — swiping it away silently re-posts it, and the alert repeats on a configurable interval until the dose is marked taken or skipped. Snooze and a configurable missed-dose grace window included.
- **Package variants with per-variant stock**: a 150 mg dose can be taken as one 150 mg capsule or two 75 mg ones — each package form keeps its own stock pool and is decremented correctly.
- **History and adherence**: a calendar with per-day status dots (statuses editable retroactively), 30-day adherence stats with per-medication breakdown and a no-miss streak.
- **Stock tracking** with low-stock notifications and refill amounts.
- **Multiple profiles** — family members in one app, reminders fire for everyone.
- **Doctor appointments** with reminders (1 day / 2 h / 30 min before).
- **Full backup as versioned YAML** — export/import through any documents provider (local file, Google Drive). Import validates the file, previews the contents and auto-saves the current data first.
- English and Russian UI, light and dark themes, Material You dynamic color.

## Install

Grab the APK from [Releases](https://github.com/nd4y/dosette/releases), or add the repo to
[Obtainium](https://github.com/ImranR98/Obtainium) for automatic updates:

1. Obtainium → **Add App**.
2. App source URL: `https://github.com/nd4y/dosette`.

Requires Android 8.0+. For reminders to arrive on time, allow notifications and grant the
battery-optimization exemption when the app asks during onboarding.

## Backup format

The backup is a single YAML file, schema version 1: `settings`, then `profiles[]`, each with nested
`medications[]` (variants, schedules with times), `dose_logs[]` and `appointments[]`. Dates are ISO
(`2026-08-29`), times are `HH:mm`, timestamps are ISO-8601 instants. Import is strict: unknown keys,
unknown enum values and dangling references are rejected before anything is written, and the previous
data is kept as an automatic backup inside the app (last 5).

## Design

Screen mockups live in [`design/mockups`](design/mockups) as design-canvas artboards. The seed color
is teal `#00696B`; with dynamic color enabled the palette follows the device wallpaper.

## Architecture notes

- Fully offline by design: no network permission is ever requested.
- Reminders are driven by a single chained `AlarmManager.setAlarmClock()` plus reconciliation on app
  start and reboot. There is deliberately **no WorkManager and no foreground service** — do not add
  them: a missed daily job in Doze means a missed reminder.
- Occurrences are computed on the fly from immutable schedule versions; only facts (taken / skipped /
  missed) are stored, so schedule edits never rewrite history.
- Backup import/export goes through the Storage Access Framework, so Google Drive works with zero
  Google API code.

## Building

- JDK 21, Android SDK 37. Local builds: `./gradlew :app:assembleDebug`.
- Checks: `./gradlew spotlessCheck :app:detekt :app:lint :app:testDebugUnitTest`.
- Screenshot suite: `./gradlew recordRoborazziDebug --tests "icu.nd4y.dosette.ui.ScreenshotTests"` —
  PNGs land in `app/src/test/screenshots`.
- Release APKs are built and signed only by the GitHub Actions release workflow on `v*` tags
  (`versionCode = major*10000 + minor*100 + patch`, one universal APK per release).

## License

[MIT](LICENSE)
