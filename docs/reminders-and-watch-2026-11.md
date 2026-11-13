# Reminders and watch improvements

## Notification behavior

Active occurrences with the same local date and scheduled time share one notification.
The expanded view names each medication, its dose and profile. Opening it lets the user
choose an individual intake; there is no ambiguous "take all" action. Once one medicine
is taken, skipped or snoozed, the notification reflects only the active remainder. The
last resolved occurrence removes it. A single remaining medication has the usual
Take / Skip / Snooze actions. Passive missed-dose notices remain separate from active
reminders. Reconciliation after restart also removes obsolete per-medicine notifications.

## Medication repeat settings

The medication editor offers general settings, no repeat, or 1 / 2 / 5 / 10 / 15 / 30 /
60 minute intervals. New and existing medicines inherit the general setting until the
user chooses an override. This affects timed reminders, not as-needed medicines without
a scheduled occurrence. The general repeat-count limit and missed-dose grace window
remain in effect, and snooze pauses repeats. Actual timing still depends on Android
exact-alarm access. Overrides survive edits, migration and YAML export/import.

## Discovering a watch

The installation wizard uses Android network service discovery for the pairing and
connection ADB services. These ports are intentionally kept distinct. With one matching
advertisement the address is filled automatically; with several devices the user selects
one. Advertisements do not prove that a device is a watch: the existing device, APK and
signature checks remain mandatory before installation. Discovery runs only while the
wizard is visible. The watch must have wireless debugging enabled and be reachable over
Wi-Fi; the pairing-code page must be open to advertise pairing. The pairing code is
entered manually. Networks that block mDNS retain the manual-address fallback.

## Wear OS Tile

Add the Dosette tile from the watch tile picker. It shows the next outstanding dose and
remaining count, including carryover. A tap opens the watch app, where intake is confirmed
explicitly. The tile requests updates when Data Layer data changes and has a freshness
interval; it distinguishes no data, a saved older day and an unreachable phone.

## Missed doses and midnight

A missed dose keeps a visible Take action on the phone. It records the actual current
intake time through the existing stock/log transaction and retains the time-selection menu.

The home widget keeps yesterday's pending doses and missed doses from the last six hours.
It also previews tomorrow's doses within the next six hours; after all current doses are
resolved, the first tomorrow slot can be shown earlier. Yesterday / Tomorrow labels keep
the occurrence date explicit. Tomorrow previews open the app rather than recording an
early intake. The ring continues to count only today. A live widget follows date rollover,
the reminder engine refreshes at midnight, and ordinary widget updates remain enabled.

## Acceptance

Regression tests cover notification shrinking and process recreation, per-medication
planner/engine settings, migration and persistence, network-discovery selection, Tile
state and midnight boundaries. Full repository gates and signed release verification are
required before publication. Physical watch/LAN behavior and actual notification sound
must be reported separately from unit tests or emulator checks.
