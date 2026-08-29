# Dosette

Offline medication tracker for Android. Material 3 Expressive, no accounts, no network — your data stays on the device.

**Status: early development.** The app is not ready for daily use yet.

## Planned features

- Medications with flexible schedules: fixed times, weekdays, every-N-days, cycles (X on / Y off), as-needed
- Persistent reminders: the notification comes back until the dose is marked taken or skipped, with a configurable nag interval
- Dose history, adherence calendar and stats
- Stock tracking with refill reminders
- Multiple profiles (family members)
- Doctor appointments with reminders
- Full backup as a versioned YAML file — export/import via any documents provider (local file, Google Drive)
- English and Russian UI, light and dark themes, Material You dynamic color

## Install

Releases will be published on GitHub and installable via [Obtainium](https://github.com/ImranR98/Obtainium). Not available yet.

## Design

Screen mockups live in [`design/mockups`](design/mockups) as design-canvas artboards. The seed color is teal `#00696B`; with dynamic color enabled the palette follows the device wallpaper.

## Architecture notes

- Fully offline by design: no network permission will ever be requested.
- Reminders are driven by a single chained `AlarmManager.setAlarmClock()` plus reconciliation on app start. There is deliberately **no WorkManager and no foreground service** — do not add them: a missed daily job in Doze means a missed reminder.
- Backup import/export goes through the Storage Access Framework, so Google Drive works with zero Google API code.

## Building

- JDK 21, Android SDK 36. Local builds: `./gradlew :app:assembleDebug`.
- Checks: `./gradlew spotlessCheck :app:detekt :app:lint :app:testDebugUnitTest`.
- Release APKs are built and signed only by the GitHub Actions release workflow on `v*` tags.

## License

[MIT](LICENSE)
