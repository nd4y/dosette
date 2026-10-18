# Install and update the Wear app from Dosette

Open **More > Settings > Watch > Install or update** on Android 10 or later.
The phone includes its matching, signed Wear APK; no computer, root, APK picker,
external download or installation permission on the phone is needed.

1. Put the phone and Wear OS 3+ watch on the same reachable Wi-Fi network.
   Keep the watch charging. Enable developer options, ADB debugging and wireless
   debugging on the watch. The wizard explains the steps.
2. Open **Pair new device** on the watch and enter its numeric IPv4 address,
   pairing port and six-digit code. Keep the pairing window open. Pairing this
   computer does not authorize the phone app's separate ADB identity.
3. Return to the watch's main wireless debugging page and enter its **connection**
   port. This is usually different from the pairing port and can change.
4. Review the verified certificate and installed/included versions, then confirm
   Install or Update. The wizard rechecks the target and installed APK immediately
   before installing, and verifies the installed APK again afterward.
5. Open Dosette on the watch, check phone synchronization, then disable debugging.

## Trust and data preservation

- Only Dosette's own bundled APK can be installed. Google's apksig verifies the
  complete APK signature; metadata must declare Dosette's package and required
  watch feature. Its sole current signer must match the phone app's signer.
- The installed watch base APK is read over ADB and independently verified.
  Different signing certificates and lower incoming version codes are blocked.
  An equal version is reported as current and no replacement occurs.
- Installation uses Android PackageInstaller sessions with `-r`, without `-d`,
  uninstall, clear-data, runtime-permission grants or a shared-storage APK.
  An unsuccessful uncommitted session is abandoned when the connection permits.
  A normal accepted update keeps app preferences and queued watch actions.
- Cancel/timeouts close the transport and interrupt the worker. A lost reply
  after commit means the outcome is unknown: reconnect to inspect the actual
  version. Never uninstall or downgrade to recover from this uncertainty.
- ADB grants broader debugging access than ordinary Dosette synchronization.
  Pairing follows the standard SPAKE2/TLS ADB trust model. Client RSA credentials
  are AES-GCM encrypted with an Android Keystore key in `noBackupFilesDir`;
  codes are not saved or logged. App reinstall/device migration requires pairing
  again. Debugging is never silently enabled on the watch.

## Build and checks

Each phone variant's generated assets depend on the matching Wear APK build.
Release verification checks that the phone APK and AAB embed byte-for-byte the
separately verified Wear APK. Both halves must use the same signing identity,
including mixed store/direct installations. Native ABI and 16 KB gates remain.
The vendored pure Java ADB transport's provenance/licenses are in `adb/README.md`.

Run the normal CI gates, including `:adb:testDebugUnitTest` and `:adb:lint`, plus
the watch installer protocol, signature-tampering, ViewModel and screenshot tests.
Mock protocol tests establish ordering/blocked operations, not actual data
preservation or physical-watch acceptance. Recheck real Android/Wear devices,
wrong/expired pairing codes, interrupted transfer/commit, app restart, same/newer
installed versions and data/queued-action survival before claiming those pass.

Reference: https://developer.android.com/training/wearables/get-started/debug-wifi
