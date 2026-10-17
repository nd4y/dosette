# Google Play publication checklist

Reviewed against official documentation on 2026-10-18. This repository prepares artifacts; it does not create a developer account, pay fees, submit declarations, or publish to Google Play.

## Account and publication gates

1. The owner intends to publish as an individual and has no organization or sole-proprietor registration. Confirm the correct account type **before paying**. Google's [account-type guidance](https://support.google.com/googleplay/android-developer/answer/13634885?hl=en) directs health apps, including medical apps, toward an organization account, which requires a D-U-N-S number. Dosette manages medication schedules and must declare that health feature. Confirm whether Dosette's exact scope qualifies for a personal account with Play support; do not misclassify the app or invent an organization.
2. The owner completes identity/contact verification and the [one-time US$25 registration](https://support.google.com/googleplay/android-developer/answer/6112435?hl=en). Set the app price to **Free**. This is a developer registration charge, not an app download charge.
3. Provide a real public support email, developer identity and the intended distribution countries. These are owner decisions, not inferred from GitHub.
4. If a new **personal** account is accepted, run a [closed test with at least 12 continuously opted-in testers for at least 14 days](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en), collect actual feedback, then apply for production access. Meeting the count alone does not guarantee approval.
5. Finish the declarations, assets and real-device checks below, then submit both form-factor tracks for review. No public listing or production approval has been verified yet.

## One app, two artifacts, one permanent signing identity

Keep application ID **icu.nd4y.dosette** for both modules. Create one Play app and add the Wear OS form factor/dedicated track. Upload the phone AAB to the phone track and the Wear AAB to the Wear OS track. The watch is a companion and requires Dosette on the paired Android phone. Its manifest declares the required watch feature and standalone=false. See [Wear packaging](https://developer.android.com/training/wearables/packaging) and [dedicated form-factor tracks](https://support.google.com/googleplay/android-developer/answer/13264522?hl=en).

When enrolling in [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en), choose the option to supply the **existing app signing key**, following Google's secure export/import instructions. Do not accept a new Google-generated app signing key for this existing package without a separately planned migration.

The existing public certificate SHA-256 is:

    678b5ad85a8cdea2b74654aef067f8266aa699e3caf44ca2e6b616c5b613f649

This is a public identity, not a private key. Verify it against Play's **app signing certificate**, not only the upload certificate. Changing it would break GitHub/Obtainium upgrades and Data Layer communication between differently signed phone/watch installs. Keep the original keystore and protected backup; never commit it or its passwords. The current workflow signs APKs and AABs with this key. A later separate upload-key migration requires updating AAB verification deliberately.

Version codes use a shared namespace:

    base = major * 10000 + minor * 100 + patch
    phone = base * 10
    wear = base * 10 + 1

For example, v0.13.2 produces 13020 and 13021, above the historical v0.13.1 code 1301. Minor and patch must be 0..99; release 0.0.0 and overflow are rejected. Each subsequent release must use a higher semantic version. Once a versionCode is uploaded to any Play track, use a new version for different binaries; do not rebuild different commits under the same tag.

## Build and verification

- CI runs format/static checks, unit/screenshot tests, domain coverage and Android lint, then builds minified APKs and AABs with debug signing for inspection.
- The **Release** workflow can run manually with a version input. It produces signed preview artifacts and R8 mappings as an Actions artifact without creating a GitHub Release or submitting to Play. The Actions UI may expose dispatch only from the default-branch workflow. This preparation branch was successfully dispatched with gh workflow run release.yml --ref fix/play-release-readiness --field version=v0.14.0; the resulting run still uses the selected branch and its gates.
- A vX.Y.Z tag runs the same checks, signs with the existing repository secrets and creates a GitHub Release with both APKs, both AABs and SHA-256 files.
- Existing secrets remain KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD. They are never printed; the temporary keystore is removed even on failure.
- A versioned Gradle invocation requires complete release signing configuration. An unversioned local release build is a debug-signed smoke build and must not be distributed.
- APK names remain dosette-vX.Y.Z.apk and dosette-wear-vX.Y.Z.apk. Obtainium's phone APK filter remains ^dosette-v.

The verifier checks actual APK/AAB package and version metadata, signatures, the pinned release certificate, current target APIs, absence of restricted USE_EXACT_ALARM, required watch targeting, native 32/64-bit library pairs and 16 KB ELF/ZIP alignment. It does not replace Play's bundle validation or installation and behavior checks on actual devices.

After building, local smoke checks are:

    python -m unittest discover -s scripts -p "test_*.py"
    python scripts/verify_release.py --smoke

Use --version vX.Y.Z for signed release verification instead of --smoke. JDK tools and ANDROID_HOME must be available.

## Platform and permission requirements

| Item | Current implementation / required evidence |
| --- | --- |
| Target API | Both modules target 36. Current minimum is phone 36 / Wear 35 since 2026-08-31. Recheck at submission using the [official policy](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en). |
| Wear 64-bit | Required since 2026-09-15. Pure Kotlin/Java is architecture-independent; bundled native libraries are checked for 64-bit counterparts. Install on a 64-bit watch/emulator as well. |
| 16 KB pages | Native libraries and APK alignment are checked. Test an installed Play-generated build on a 16 KB device/emulator. See [Android page-size guidance](https://developer.android.com/guide/practices/page-sizes). |
| Exact reminders | Use user-granted SCHEDULE_EXACT_ALARM, with an inexact fallback when denied. Do not restore restricted USE_EXACT_ALARM just to avoid the permission flow. Confirm granting/revoking access and reboot recovery on device. |
| Background location | Retained only for optional "snooze until home/work". This triggers Play's separate background-location review; approval is not guaranteed. Supply the feature declaration and a short video showing the prominent disclosure, permission flow and actual reminder on arrival. Timed reminders must work without location. |
| Battery exemption | Explain why medication reminders need timely delivery; the user may decline. Validate behavior with and without exemption rather than promising delivery under every device power policy. |

Sources: [Wear quality](https://developer.android.com/docs/quality-guidelines/wear-app-quality), [sensitive permissions](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en), [background-location review](https://support.google.com/googleplay/android-developer/answer/9799150?hl=en). If Play rejects optional background location as insufficiently central to the app, choose a deliberate product change or remove it from the submitted variant and accurately describe the reduced functionality.

## App content and Data safety

- Complete **Health apps** and select **Medication and Treatment Management**. Assess the appointment reminder feature against the other categories shown in the current form. Do not claim regulatory approval or select "no health features". [Health declaration](https://support.google.com/googleplay/android-developer/answer/14738291?hl=en).
- Keep the non-medical-device disclaimer and instruction to consult a healthcare professional in the store description and in-app About screen. Dosette records user-entered schedules; it does not prescribe treatment. [Health policy](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en).
- Declare no ads and no account/login requirements based on the current source. Complete the content-rating and target-audience forms using the real intended audience. Family profiles do not make this a child-targeted app.
- Publish [privacy-policy.html](privacy-policy.html) at a stable public HTTPS URL without authentication/geofencing and add that URL in Play Console. In-app privacy text is also available offline. The owner must add a real private support contact before submission; public GitHub issues must never receive medical details.
- Data safety is an evidence-based owner declaration. Do **not** choose "no data collected" simply because Dosette lacks INTERNET permission. Inspect the release's merged manifest and dependency versions and the [SDK disclosure documentation](https://developers.google.com/android/guides/play-data-disclosure).

Data inventory for that review:

| Data/path | Storage and transfer to examine |
| --- | --- |
| Profiles, medications, doses, stock, appointments | Stored on the phone. No Dosette account or developer backend. No advertising/analytics SDK in the application dependencies. |
| Watch snapshot and queued actions | Shared with the paired device through Google Play services. Data Layer uses Bluetooth or Google's cloud relay when Bluetooth is unavailable; cloud transport is end-to-end encrypted. |
| Places and location | Optional saved labels/coordinates/radius/Wi-Fi names on the phone, used with Google Play services location/geofencing. Review the SDK's own disclosures and system location settings. |
| Backups | User-selected export destination, optionally encrypted. A selected cloud document provider processes the exported file under its own policy. Automatic pre-import copies remain in private app storage (last five). |
| Diagnostics/contact | No automatic developer telemetry in the app. Information voluntarily posted to GitHub is public and subject to GitHub's policy. |

[Data Layer transport](https://developer.android.com/training/wearables/data/overview) and [Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) distinguish local processing, end-to-end-encrypted transfers and user-initiated sharing. Apply exemptions only after checking their conditions, SDK behavior and actual release artifacts. No final Console answers have been submitted.

## Listing and privacy hosting

Draft English/Russian text is in [store-listing.md](store-listing.md). Prepared assets and their provenance are in [assets/README.md](assets/README.md): a 512x512 Play icon, localized 1024x500 feature graphics and a dedicated Wear screenshot set. Refresh screenshots from the final accepted build where needed. Use fictitious medication/profile data. Wear screenshots should be square, show only the real interface, and contain no device frame, transparency or circular mask. Check [current asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).

The policy is a static HTML file with no analytics or third-party assets. It can be served on an existing public HTTPS site. With GitHub Pages, configure the approved branch /docs directory and verify the resulting URL; the expected path would be /dosette/google-play/privacy-policy.html. Do not enter that URL into Console until it actually serves the reviewed page. No Pages configuration or deployment is performed by this change.

## Device acceptance before production

Record device/OS, build version, date, result and any limitation. Use test data and keep medical details out of logs/screenshots.

- Install Play-delivered phone and Wear builds, then check their installed certificate identity. Also test GitHub-to-Play updates and a mixed GitHub/Play phone-watch pair.
- New phone install: allow and deny notifications/exact alarms, change permissions later, test reboot before and after first unlock, timezone/DST changes, Doze, repeated notifications and missed-dose handling.
- Watch: small/large round screens, large fonts, swipe-back, phone absent/disconnected, reconnect after queued Take/Skip/Snooze actions, duplicate delivery and correct stock/history.
- Location: deny/approximate/precise/all-the-time paths, disable location, actually leave/enter the configured place, and verify the timed fallback. A mocked geofence is insufficient.
- Export/import with and without encryption, malformed input, rollback backup and deletion from phone/watch/export provider.
- Inspect Play pre-launch reports, resolve blocking findings and repeat affected scenarios. Save real closed-test feedback if the personal-account route applies.

Rollback: keep the previous source tag and a user-exported backup. Play and Android require increasing versionCode, so ship a new forward-fix release if needed; do not attempt a production version downgrade or restore an old database without a verified compatible backup.
