Here is a comprehensive release-readiness evaluation of **Fillmark** (`ca.bralten.fillmark`), covering **Play Store blockers**, **functional bugs**, **missing user-facing capabilities**, **polish & accessibility**, and **Play Console/metadata setup**.

Overall, Fillmark has a clean Kotlin/Compose/Room/Glance foundation and a solid core logging loop, with passing unit tests for its core math utilities. However, there are **several hard Play Store blockers**, **noticeable UX bugs (especially Dark Mode and stale daily state)**, and **key capability gaps (notably reminders, onboarding, and backup/export)** to address before a public Play Store release.

---

## 1. Play Store Blockers & Build/Release Setup (Must Fix to Ship)

### Target API, Release Build & Signing
- **`targetSdk = 35` fails Google Play's API minimum:** [app/build.gradle.kts](file:///c:/Users/kevin/Dev/fillmark/app/build.gradle.kts#L27) compiles against SDK 36 but targets SDK 35. Google Play now requires new apps and updates to target **Android 16 (API 36)** (enforcement started Aug 31, 2026; extension ended Nov 1, 2026).
- **No release signing, AAB build, or R8 shrinking:** [app/build.gradle.kts](file:///c:/Users/kevin/Dev/fillmark/app/build.gradle.kts#L15-L40) only configures `debug` signing (with a committed `debug.keystore`), sets `isMinifyEnabled = false`, omits `isShrinkResources = true`, and references `"proguard-rules.pro"` — **which does not exist** in `app/`. Similarly, [firebase-distribution.yml](file:///c:/Users/kevin/Dev/fillmark/.github/workflows/firebase-distribution.yml#L35-L43) only builds `assembleDebug` APKs rather than signed release `.aab` bundles.
- **Duplicate bloated dependency without shrinking:** `androidx.compose.material:material-icons-extended` is declared twice ([app/build.gradle.kts](file:///c:/Users/kevin/Dev/fillmark/app/build.gradle.kts#L81) and [L123](file:///c:/Users/kevin/Dev/fillmark/app/build.gradle.kts#L123)). Without R8 minification, this library alone adds tens of megabytes to the package.
- **Android Lint error blocks release builds:** `./gradlew lintDebug` currently fails with **1 error** (and 61 warnings): `UnusedContentLambdaTargetStateParameter` in [AnalyticsScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/analytics/AnalyticsScreen.kt#L188-L208), where `AnimatedContent(targetState = uiState.periodOffset) { _ -> ... }` ignores its lambda parameter (which also breaks the slide transition visually).

### Health Connect & Privacy Policy Compliance
- **Alpha Health Connect SDK:** [libs.versions.toml](file:///c:/Users/kevin/Dev/fillmark/gradle/libs.versions.toml#L20) uses `1.1.0-alpha11` while stable `1.1.0` is available.
- **Non-functional rationale screen & missing Privacy Policy link:** In [HealthConnectRationaleActivity.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/sync/HealthConnectRationaleActivity.kt#L59-L73), both **"Continue"** and **"Not Now"** simply call `finish()`, and there is no link to a hosted privacy policy. Play's Health Connect review requires the privacy policy displayed in the Health Connect rationale / `VIEW_PERMISSION_USAGE` flow to match a live, publicly hosted HTTPS Privacy Policy URL on the Play listing.
- **Cloud Auto Backup contradicts local-only privacy claims:** [AndroidManifest.xml](file:///c:/Users/kevin/Dev/fillmark/app/src/main/AndroidManifest.xml#L17) sets `android:allowBackup="true"` without wiring up `android:fullBackupContent` or `android:dataExtractionRules` ([backup_rules.xml](file:///c:/Users/kevin/Dev/fillmark/app/src/main/res/xml/backup_rules.xml) and [data_extraction_rules.xml](file:///c:/Users/kevin/Dev/fillmark/app/src/main/res/xml/data_extraction_rules.xml) are unused template files). Android Auto Backup will silently upload local Room/DataStore hydration data to Google Drive, contradicting the README and rationale text (*"no cloud servers"*) and potentially causing a Data Safety mismatch. Either explicitly disable cloud backup (`allowBackup="false"` or exclude Room/DataStore from `<cloud-backup>` while allowing `<device-transfer>`) or document it accurately.
- **Manifest & edge-to-edge hygiene:**
  - [AndroidManifest.xml](file:///c:/Users/kevin/Dev/fillmark/app/src/main/AndroidManifest.xml#L90-L100): `WidgetMidnightReceiver` is `exported="true"` with no permission, exposing internal action `com.fillmark.app.action.MIDNIGHT_RESET` to any app (Lint `ExportedReceiver`).
  - [AndroidManifest.xml](file:///c:/Users/kevin/Dev/fillmark/app/src/main/AndroidManifest.xml#L22): `android:windowSoftInputMode="adjustResize"` is placed on `<application>` instead of `<activity>` (`MainActivity`), where it has no effect.
  - [HealthConnectRationaleActivity.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/sync/HealthConnectRationaleActivity.kt#L26) does not call `enableEdgeToEdge()`, risking system bar overlap under Android 15/16 enforced edge-to-edge.
  - [MidnightAlarmScheduler.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/MidnightAlarmScheduler.kt#L39-L63) calls `setExactAndAllowWhileIdle` / `setExact` without `SCHEDULE_EXACT_ALARM` (Lint `MissingPermission`). Midnight widget refreshes do not qualify for exact-alarm Play policy exceptions and should rely on inexact alarms / WorkManager + `DATE_CHANGED`.

---

## 2. Functional Bugs & Quality Defects in Existing Code

### High-Impact UX Bugs
1. **Dark Mode is largely unreadable:** [Theme.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/theme/Theme.kt#L68-L81) automatically adopts `DarkColorScheme` (`background = #091420`) when the system is in dark mode, yet nearly every screen hardcodes light-mode colors such as `FillmarkNavyTitle` (`#0D1E32` — near-black on a near-black background!), `FillmarkCardBorder` (`#E2E8F0`), `Color(0xFFE2EAF1)`, and pastel pace pills across [DashboardScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/DashboardScreen.kt#L133), [FluidProgressMeter.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/components/FluidProgressMeter.kt#L234), [HistoryScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/history/HistoryScreen.kt#L104), [AnalyticsScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/analytics/AnalyticsScreen.kt#L87), [SettingsScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/settings/SettingsScreen.kt#L101), [DateTimePickerDialog.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/common/DateTimePickerDialog.kt#L86), and [Navigation.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/Navigation.kt#L103-L109). There is also no `values-night/themes.xml`, causing a white splash flash on cold start in dark mode.
2. **Dashboard date & pacing go stale while app stays in memory:**
   - [DashboardViewModel.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/DashboardViewModel.kt#L56) calls `repository.observeTodaySummary()` once in `init`, capturing `LocalDate.now()` into static day-boundary timestamps ([HydrationRepository.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/data/repository/HydrationRepository.kt#L61-L81)). Leaving the app open across midnight leaves Today stuck on yesterday until process restart.
   - `PacingCalculator.evaluatePacing(LocalTime.now(), ...)` only recomputes when DB or preferences change — resuming the app hours later displays stale pacing until a drink is logged.
3. **Historical target preservation is unimplemented:** `DailyTargetDao` and `HydrationRepository.setTargetForDate()` exist, but `setTargetForDate()` is **never called anywhere**. Editing the goal in Settings overwrites `defaultDailyTargetMl`, retroactively changing targets and goal-hit rates for all historical days in Analytics.
4. **Settings Daily Target input bugs:**
   - Re-enabling the goal switch in Imperial mode passes `85.0` (fl oz) into `VolumeConverter.formatNumberOnly()` which expects milliliters ([SettingsScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/settings/SettingsScreen.kt#L162-L164)), briefly rendering `"2.9"`.
   - Every keystroke writes to DataStore, which retriggers `remember(uiState.defaultTargetMl, uiState.displayUnit)` and reformats `targetInput` mid-edit (fighting decimal typing in Imperial mode).
5. **Health Connect sync reliability gaps:**
   - **No periodic inbound sync:** `HealthConnectSyncWorker` is only enqueued as a one-time job when the user modifies records inside Fillmark or toggles sync ([HydrationRepository.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/data/repository/HydrationRepository.kt#L159-L170)). Drinks logged in other apps are never synced in the background on a schedule (`PeriodicWorkRequest`) or on app foreground.
   - **No deletion/update handling on inbound sync & no initial backfill:** [HealthConnectManager.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/sync/HealthConnectManager.kt#L180-L226) ignores `DeletionChange`, skips updates to already-imported records, and on first run only stores a token without importing recent history.
   - **Undo-delete breaks Health Connect sync:** Deleting a synced record in History deletes it from Health Connect ([HydrationRepository.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/data/repository/HydrationRepository.kt#L125-L134)), and tapping **Undo** ([HistoryViewModel.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/history/HistoryViewModel.kt#L102-L109)) re-inserts it with the stale `healthConnectId` and `lastSynced`, so it is never re-uploaded.
   - **Widget HC sync bypasses WorkManager:** [QuickLogActionCallback.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/QuickLogActionCallback.kt#L59-L66) spawns an unattached `CoroutineScope(Dispatchers.IO)` that the OS can kill immediately after the broadcast finishes.
   - **No Play Store prompt on Android 10–13:** [SettingsScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/settings/SettingsScreen.kt#L261-L272) treats `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED` the same as completely unavailable instead of offering an "Install Health Connect" link. No "Sync Now" button or last-synced status exists.

### Medium-Impact Bugs & Rough Edges
- **Analytics bugs:**
  - [BarChart.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/analytics/components/BarChart.kt#L103-L139) draws a `28dp` percentage pill on *every* bar, causing severe overlap and unreadable labels in **Month (30 bars)** and **Year (52 bars)** views.
  - [AnalyticsViewModel.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/analytics/AnalyticsViewModel.kt#L298-L307) calculates `daysGoalMet = met * 7` for `3M` and `Year` (multiplying qualifying *weeks* by 7 instead of counting actual days) and ignores per-day targets.
  - [DailyLineChart.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/analytics/components/DailyLineChart.kt#L292) displays *"No drink entries recorded yet today"* even when viewing a past date.
- **Database seeding race & schema tracking:**
  - [FillmarkDatabase.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/data/local/FillmarkDatabase.kt#L121-L129) seeds `DEFAULT_PRESETS` inside `onCreate` via `INSTANCE?.let { ... }` on an async scope — a known Room race condition where `INSTANCE` may still be `null` during `build()` or presets may appear missing on first launch.
  - `exportSchema = false` ([FillmarkDatabase.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/data/local/FillmarkDatabase.kt#L28)) prevents automated Room migration testing for future schema upgrades.
- **State loss & dead code:**
  - [Navigation.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/Navigation.kt#L63) uses `remember` instead of `rememberSaveable` for `currentTab`, resetting the user to Today on rotation/config change.
  - Navigation 3 dependencies and [NavigationKeys.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/NavigationKeys.kt) are unused; [HistoryViewModel.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/history/HistoryViewModel.kt#L43-L68) leaves verbose `Log.d` calls in production code.
- **UI & widget glitches:**
  - [CustomizePresetsDialog.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/components/CustomizePresetsDialog.kt#L189-L194) shows a `DragHandle` icon (and imports `detectDragGesturesAfterLongPress`), but drag-to-reorder is not implemented.
  - [QuickLogBarWidget.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/QuickLogBarWidget.kt#L86-L89) says `"Tap to open Fillmark"` when empty, with no click handler attached.
  - [PresetEditorDialog.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/components/PresetEditorDialog.kt#L349) hardcodes `displayOrder = 99` for new presets and gives no validation error when saving blank/invalid inputs; decimal inputs allow multiple dots (`"12.3.4"`) and unbounded values (e.g., `9999999 ml`).
  - [DashboardScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/DashboardScreen.kt#L224-L230) items in Today's Intake are not clickable to edit/delete; manual logs pass `iconKey = null`, always showing the glass icon even for Coffee/Soda.
  - **Broken instrumented test:** [MainScreenTest.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/androidTest/kotlin/com/fillmark/app/ui/main/MainScreenTest.kt#L17) is a leftover template referencing a nonexistent `MainScreen` composable and will not compile. There are also zero tests for DAOs, migrations, `HydrationRepository`, `HealthConnectManager`, ViewModels, UI, or widgets.

---

## 3. Missing Capabilities Expected by Public App Users

| Capability | Current State | Why Users Expect It |
|---|---|---|
| **Hydration Reminders & Notifications** | Completely absent (`POST_NOTIFICATIONS` not declared) | Core reason many users install a drink tracker. Smart pacing-aware reminders ("only notify if behind pace during waking hours") with quick-log notification actions would be a standout differentiator matching Fillmark's pacing model. |
| **First-Run Onboarding** | Drops directly into `0 ml / 2500 ml` Metric, `7am–11pm` | A brief 2–3 screen setup (unit preference defaulted by locale, daily goal with optional guidance, wake/sleep schedule, optional reminders & Health Connect) makes the app immediately relevant. |
| **Local Backup / Export / Restore (JSON & CSV)** | Absent | Critical for a local-first, no-account app. Users need a way to transfer history to a new phone or export health logs, plus a "Reset / Clear Data" option in Settings. |
| **Theme Control & True Dynamic Color** | Hardcoded to follow system (broken in dark mode); `dynamicColor = false` | Users expect Light / Dark / System selector in Settings (and README promises Material 3 dynamic styling). |
| **About, Privacy Policy & Licenses Screen** | Settings ends after Widgets; `buildConfig = false` | Public apps need visible app version, Privacy Policy link, GPL-3.0 / open-source notices, and a feedback/bug-report link. |
| **Habit & Insight Polish** | 4th Analytics card just shows `"Active Unit: ml"` | Replace with meaningful stats: **Current/Best Streak**, **Drink Type Breakdown**, tap-a-bar to inspect a day, and the README-promised **"All Time"** view. |
| **Per-Day Goal Override & Factor Toggle** | Only global default target; fixed hydration factors per drink | Let users adjust today's goal (e.g., workout/hot day) without altering history, and optionally toggle hydration factors off (1:1 tracking) or customize them. |
| **Android Platform Polish** | No splash screen, app shortcuts, or Quick Settings tile | Adding `core-splashscreen`, launcher App Shortcuts (long-press icon to quick-log), Quick Settings Tile, and widget undo support elevates perceived quality to top-tier Play Store apps. |

---

## 4. Assets, Localization & Accessibility

### Branding & Visual Assets
- **Default Android Robot legacy icons still in repo:** All raster launcher icons in `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` (and `ic_launcher_round.webp`) are still Android Studio's **default green Android robot**! Only `mipmap-anydpi-v26` was customized. Regenerate all raster mipmaps from the Fillmark artwork (and move `mipmap-anydpi-v26` to `mipmap-anydpi` since `minSdk = 29`).
- **Broken Material You themed icon:** [ic_launcher.xml](file:///c:/Users/kevin/Dev/fillmark/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml#L5) points `<monochrome>` to the multi-color gradient `ic_launcher_foreground`, which renders poorly on Android 13+ themed home screens. Create a clean single-color monochrome silhouette drawable.
- **Widget previews & initial layouts:** `widget_preview_*.xml` drawables are low-fi geometric placeholders (triggering Lint `VectorRaster` warnings at `280dp` width) and `widget_preview_full_dashboard.xml` doesn't match the actual 5-preset layout. Also replace `android:initialLayout="@android:layout/list_content"` with a clean Glance loading layout and add Android 12+ `android:previewLayout`.

### Internationalization & Locale Formatting
- **Hardcoded English strings everywhere:** Almost all UI copy is hardcoded in Kotlin instead of [strings.xml](file:///c:/Users/kevin/Dev/fillmark/app/src/main/res/values/strings.xml) (which only has 6 strings). This also causes grammar bugs like `"1 entries"` ([DashboardScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/DashboardScreen.kt#L195)) instead of plural resources.
- **Ignored 24-hour clock & locale:** [TimeUtils.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/core/TimeUtils.kt#L13-L16), [DateTimePickerDialog.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/common/DateTimePickerDialog.kt#L293), and [SettingsScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/settings/SettingsScreen.kt#L471) hardcode `Locale.US` and 12-hour AM/PM (`is24Hour = false`) instead of respecting `DateFormat.is24HourFormat(context)` and device locale. Defaulting US locales to Imperial (`fl oz`) on first run is also standard practice.

### Accessibility & Large Screens
- **Zero TalkBack semantics on custom graphics:** `FluidProgressMeter`, `BarChart`, and `DailyLineChart` use raw `Canvas` without `Modifier.semantics { contentDescription = ... }`.
- **Undersized touch targets (< 48dp):** `CustomizePresetsDialog` reorder buttons (`24dp`), `QuickPresetBar` customize icon (`32dp`), `HistoryRecordItem` delete button (`36dp`), and `PeriodNavigationBar` arrows (`36dp`).
- **Font scaling & tablet/foldable layouts:** Fixed heights (`height(118.dp)` on `PresetCard`, `height(380.dp)` on `CustomizePresetsDialog`) will clip text at large system font sizes. No `WindowSizeClass` / responsive max-width or `NavigationRail` layout exists for tablets, foldables, or landscape mode.
- **License header mismatch:** Project license is GPL-3.0 ([COPYING](file:///c:/Users/kevin/Dev/fillmark/COPYING)), while [Color.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/theme/Color.kt#L1-L15) and [Theme.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/theme/Theme.kt#L1-L15) still carry `Copyright 2026 The Android Open Source Project / Apache 2.0` template headers (plus ~230 lines of commented-out theme code in `Color.kt`).

---

## 5. Google Play Console, Metadata & Legal Checklist (Off-Code)

1. **Developer Account & Testing Gate:**
   - Verify whether publishing with **Health Connect** requires a **Google Play Organization Account (D-U-N-S number)** vs. personal account for your chosen Health Apps subcategories.
   - If using a personal developer account created after Nov 2023, plan for the mandatory **12+ testers opted in for 14 consecutive days** closed-testing gate before production access.
2. **Play App Signing & CI Release Pipeline:**
   - Generate a production upload keystore (never committed to git), enroll in Play App Signing, build signed `.aab` via `./gradlew bundleRelease`, and upload `mapping.txt` for readable Play Console crash/ANR reports.
3. **Play Console Policy Forms:**
   - **Privacy Policy:** Publish a clean, public HTTPS webpage (not a PDF) explaining on-device storage, Health Connect usage, and zero telemetry; link it in both Play Console and the in-app Settings + Health Connect rationale screen.
   - **Health Apps Declaration + Health Connect Data Type Declaration:** Declare `READ_HYDRATION` and `WRITE_HYDRATION` with clear user-facing justifications and be ready to provide reviewer instructions (and a demo video if requested).
   - **Data Safety Form:** Ensure disclosures match actual behavior (especially regarding Android Auto Backup and Health Connect).
   - **Content Rating (IARC), Target Audience, Ads Declaration (None), App Access.**
4. **Store Listing Assets & Copy:**
   - `512×512` hi-res icon, `1024×500` feature graphic, and 6–8 polished phone screenshots (Dashboard gauge & pacing, Quick Presets, Home Screen Widgets, Daily/Weekly Analytics, Dark Mode, Health Connect) — plus tablet screenshots if supporting large screens.
   - Short description (≤80 chars) & Full description (≤4,000 chars) including a **non-medical-device disclaimer** (e.g., *"Fillmark is for general wellness tracking only and is not a medical device"*).
   - Update [README.md](file:///c:/Users/kevin/Dev/fillmark/README.md) to fix the placeholder clone URL and align feature claims with the actual app.

---

## Recommended Release Roadmap

1. **Phase 1 — Compliance & Release Plumbing:** Bump `targetSdk = 36`, upgrade Health Connect to stable `1.1.0`, add `proguard-rules.pro` + R8 shrinking + release signing/AAB CI, fix the Compose Lint error, fix `allowBackup`/backup rules, secure `WidgetMidnightReceiver`, fix `HealthConnectRationaleActivity` + Privacy Policy URL, regenerate raster/monochrome launcher icons.
2. **Phase 2 — Core Bug & Quality Sweep:** Fix Dark Mode color tokens across all screens, fix midnight/time reactivity in `DashboardViewModel`, wire up `DailyTargetDao` historical target preservation, fix Settings target input & Imperial bug, fix `BarChart` overlap & `3M`/`Year` goal-hit math, add periodic/foreground Health Connect inbound sync + Undo fix, fix `DatabaseCallback` seeding race, and repair/expand automated tests (including `MainScreenTest` and Room migration tests).
3. **Phase 3 — Public-Quality Features & Polish:** Add smart pacing-aware Reminders/Notifications, First-Run Onboarding, JSON/CSV Backup & Restore, Settings About/Privacy/Theme section, full `strings.xml` + 24-hour/locale formatting, TalkBack semantics & 48dp touch targets, and `core-splashscreen`.
4. **Phase 4 — Store Submission:** Complete Play Console Health Connect & Data Safety declarations, prepare store graphics/screenshots, run Closed Testing + Play Console Pre-launch Report across real devices, then promote to Production.

If you'd like, we can start tackling these phases right away (for example, starting with Phase 1 blockers or fixing Dark Mode and core bugs).