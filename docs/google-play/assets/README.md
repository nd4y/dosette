# Store graphics

Prepared and visually inspected on 2026-10-18. These are publication assets, not proof of Google Play approval.

| Asset | Format | Provenance / suggested alt text |
| --- | --- | --- |
| icon-512.png | 512x512, 32-bit PNG, opaque background | Exact launcher capsule geometry and #00696B background from app/src/main/res/drawable/ic_launcher_foreground.xml and values/colors.xml. Alt: Dosette capsule icon. |
| feature-en-1024x500.png | 1024x500, 24-bit RGB PNG without alpha | English feature artwork using the existing teal palette, Segoe UI typography and an illustrative daily schedule. Alt: Dosette medication schedule for Android and Wear OS. |
| feature-ru-1024x500.png | 1024x500, 24-bit RGB PNG without alpha | Russian version of the same composition. Alt: Dosette: расписание лекарств для Android и Wear OS. |
| wear-ru/01-dose-actions.png | 454x454 PNG, every pixel opaque | Unchanged copy of wear/src/test/screenshots/watch_dose_pending.png. Alt: Кнопки приёма, пропуска и откладывания напоминания на часах. |
| wear-ru/02-dose-history.png | 454x454 PNG, every pixel opaque | Unchanged copy of wear/src/test/screenshots/watch_dose_taken.png. Alt: Отмеченный приём лекарства в приложении на часах. |

The three editable SVG counterparts are included. PNGs and SVGs are produced by the same layout recipe in scripts/generate_store_assets.ps1, using Windows System.Drawing and the installed Segoe UI font. No font file, third-party artwork or AI-generated image is embedded or redistributed. The script is UTF-8 with BOM for Windows PowerShell 5.1 compatibility.

Regenerate from the repository root on Windows:

    powershell.exe -NoProfile -File scripts/generate_store_assets.ps1

The feature artwork is an illustration, not a screenshot. It does not include store badges, price claims, device frames or health outcome claims.

The Wear images are actual Compose/Roborazzi renderings of production screen components with synthetic fixtures from WatchScreenshotTests.kt (API 34 / large-round watch qualifier). They are not physical-watch captures. They contain no added text, frame or circular screenshot mask, and have not been resized, padded or retouched. The full-list watch_today.png fixture was omitted because its lower rows are visibly clipped at the round-screen edge. Capture refreshed images from the final accepted build if these screens change; Play review and physical-watch appearance remain unverified.

Phone screenshots are stored separately under phone; their capture and synthetic-data provenance are recorded in the task's review evidence. Do not replace them with mockups or personal medical data.

Validation performed: PNG IHDR dimensions/color types, all-pixel opacity for both Wear images, byte-for-byte SHA-256 equality of Wear source/copy, SVG XML parsing, and visual inspection of the icon, both feature graphics and Wear screenshots. Current [Google Play asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en) and [Wear screenshot requirements](https://developer.android.com/docs/quality-guidelines/wear-app-quality) still apply at submission.
