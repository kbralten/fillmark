# Fillmark — Public Release Readiness Evaluation

## 2. Missing Capabilities Expected in a Public Hydration App

2. **History Search, Filtering, Date Jump & Quick-Add:**
   - [HistoryScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/history/HistoryScreen.kt) renders every historical record in one flat list with no date picker jump, search, or drink-type filter.
   - Tapping a record in History opens [EditLogDialog.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/history/components/EditLogDialog.kt) without passing `onDelete` (inconsistent with Dashboard), and there is no swipe-to-delete (despite `README.md` claiming *"Swipe-to-delete with confirmation"*).
3. **Preserve Preset Names & Custom Hydration Factors:**
   - Logging a named preset (e.g., *"Morning Matcha"*, *"Stanley 40oz"*) only stores `drinkType` (`"TEA"`, `"WATER"`), so Today's Intake and History lose the preset name.
   - [PresetEditorDialog.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/components/PresetEditorDialog.kt) and [ManualLogBottomSheet.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/components/ManualLogBottomSheet.kt) lock `hydrationFactor` to the category default with no option to customize it (e.g., decaf coffee at `1.0x`).
4. **Optional Daily Goal Estimator (Non-Medical):**
   - Onboarding and Settings currently assume users already know a target in `ml` or `fl oz`. An optional quick-helper (weight/activity-based rough estimate with a clear general-wellness disclaimer) helps new users pick a starting goal.
5. **Tactile & Habit-Loop Feedback:**
   - Adding subtle haptic feedback (`LocalHapticFeedback`) on quick-log taps and a lightweight visual celebration/badge upon reaching 100% of the daily goal elevates day-to-day polish.
6. **Widget Theming & Layout Polish:**
   - All three Glance widgets hardcode a dark translucent navy background (`#F0182432`) instead of adapting to Light/Dark/Material You (`GlanceTheme`).
   - [FullDashboardWidget.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/FullDashboardWidget.kt#L310-L315) empty-presets text is not clickable (unlike `QuickLogBarWidget`), both preset-bar widgets append a trailing `Spacer` after the last tile (slight horizontal asymmetry), and `FullDashboardWidget` lacks a quick-add manual-log button (despite `README.md` mentioning one).

---

## 3. Localization, Accessibility, Performance & Polish

### Internationalization & Locale Bugs
- **Decimal-comma locale input bug:** [VolumeConverter.sanitizeDecimalInput](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/core/VolumeConverter.kt#L19-L31) strips `,` instead of accepting locale decimal separators, and `formatVolume` / `formatNumberOnly` / `formatInputNumber` hardcode `Locale.US`. Users on European/Latin American locales typing `16,5` on a localized decimal keyboard will get `165`.
- **Remaining hardcoded English strings outside `strings.xml`:**
  - [PacingCalculator.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/domain/PacingCalculator.kt#L91-L95) (`"Ahead of pace..."`, `"On track with daily goal"`, `"Behind pace by..."`).
  - [AnalyticsViewModel.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/analytics/AnalyticsViewModel.kt#L29-L36) & [L143-L196](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/analytics/AnalyticsViewModel.kt#L143-L196) (`AnalyticsInterval` labels and period range headers).
  - [DailyGraphCalculator.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/domain/DailyGraphCalculator.kt#L11-L14) (`"Cumulative"`, `"Rate"`), [DrinkType.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/core/DrinkType.kt#L8-L15), [PresetIcon.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/core/PresetIcon.kt#L6-L10), [ThemeMode](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/data/local/UserPreferencesRepository.kt#L23-L26), and [DEFAULT_PRESETS](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/data/local/FillmarkDatabase.kt#L70-L110).
  - All Glance widgets, callbacks, Quick Settings tile, and App Shortcuts ([FullDashboardWidget.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/FullDashboardWidget.kt), [ProgressMeterWidget.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/ProgressMeterWidget.kt), [QuickLogBarWidget.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/QuickLogBarWidget.kt), [WidgetActionCallback.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/WidgetActionCallback.kt), [FillmarkTileService.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/FillmarkTileService.kt), [ShortcutHelper.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/core/ShortcutHelper.kt)).
- **Locale-unsafe drink-name formatting:** [DashboardScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/DashboardScreen.kt#L596) and [HistoryScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/history/HistoryScreen.kt#L245) use `record.drinkType.lowercase().replaceFirstChar { it.uppercase() }` instead of localized `DrinkType` string resources (which also breaks Turkish casing rules).
- **Plural grammar (`PluralsCandidate` — 5 lint warnings):** [strings.xml](file:///c:/Users/kevin/Dev/fillmark/app/src/main/res/values/strings.xml#L165) (`analytics_days_met_subtitle`, `analytics_barchart_a11y`, `settings_exported_backup`, `settings_restored_backup`, `settings_exported_csv`) can render `"Exported 1 logs"`.
- **Translations & per-app language:** Only English (`values/strings.xml`) exists today; if launching globally, add key translations and Android 13+ `locales_config.xml`.

### Accessibility, Theme Consistency & Performance
- **Remaining undersized touch targets (< 48dp):** The target-override clickable row in [FluidProgressMeter.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/components/FluidProgressMeter.kt#L303-L313) and the `DailyModeSelector` pills in [AnalyticsScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/analytics/AnalyticsScreen.kt#L519-L534).
- **Small font sizes (`SmallSp` — 22 lint warnings):** Widget preview XMLs and Canvas chart badges use `8sp`–`10sp` text, which can be hard to read on compact or high-DPI screens.
- **Incomplete Material You / Theme adherence:**
  - [HealthConnectRationaleActivity.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/sync/HealthConnectRationaleActivity.kt#L40) ignores the user's saved `ThemeMode` and `dynamicColorEnabled` settings.
  - The Dashboard FAB ([DashboardScreen.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/dashboard/DashboardScreen.kt#L137-L138)), progress arc, and Analytics chart card hardcode `FillmarkCyan` / `FillmarkNavy800` instead of `MaterialTheme.colorScheme` tokens when Dynamic Color is enabled.
- **Main-thread O(days × records) analytics computation:** [AnalyticsViewModel.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/ui/analytics/AnalyticsViewModel.kt#L198-L538) filters `allRecords` inside per-day loops (up to 364 iterations on the Year tab) on `Dispatchers.Main`. Pre-group records by `LocalDate` once and run calculations on `Dispatchers.Default` so multi-year histories stay smooth.
- **Missing shortcut usage reporting & TileService hygiene:** [MainActivity.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/MainActivity.kt#L119-L130) does not call `ShortcutManagerCompat.reportShortcutUsed()` (Lint `ReportShortcutUsage`). [FillmarkTileService.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/main/kotlin/com/fillmark/app/widget/FillmarkTileService.kt#L46) spawns an untracked `CoroutineScope` in `onClick()` and has an obsolete `SDK_INT >= Q` check (`minSdk` is already 29).

---

## 4. Testing, CI/CD & Documentation Hygiene

### Testing & Release Verification
- **No Room migration tests:** `3.json` schema is exported, but `1.json`/`2.json` and `androidx.room:room-testing` (`MigrationTestHelper`) are absent. Add automated migration tests before shipping schema v4+.
- **Thin instrumented UI/integration coverage:** [MainScreenTest.kt](file:///c:/Users/kevin/Dev/fillmark/app/src/androidTest/kotlin/com/fillmark/app/ui/main/MainScreenTest.kt) only checks tab rendering/switching. Add instrumented or Robolectric tests covering Onboarding, quick-log + Undo, Edit/Delete, Settings target persistence, and Room-backed JSON Backup/Restore.

### CI/CD & Lint Cleanup
- **Fail fast on missing release signing secrets:** [release-aab.yml](file:///c:/Users/kevin/Dev/fillmark/.github/workflows/release-aab.yml#L41-L48) silently builds an **unsigned** AAB if `RELEASE_KEYSTORE_BASE64` is unset; fail the workflow on tagged releases when signing secrets are missing.
- **Gate Firebase distribution on tests/lint:** [firebase-distribution.yml](file:///c:/Users/kevin/Dev/fillmark/.github/workflows/firebase-distribution.yml#L34-L35) pushes debug APKs on every commit to `main` without running `testDebugUnitTest` or `lintRelease`.
- **Lint warnings:** `./gradlew lintRelease` currently passes with **0 errors, 74 warnings** (notably `UnsafeProtectedBroadcastReceiver`, `PluralsCandidate`, `ReportShortcutUsage`, `ModifierParameter`, `AutoboxingStateCreation`, `ObsoleteSdkInt`, `SmallSp`, and dependency updates).

---

## 5. Google Play Console, Metadata & Legal Checklist (Off-Code)

1. **Developer Account & Testing Gate:**
   - Verify whether publishing with **Health Connect** requires a **Google Play Organization Account (D-U-N-S number)** vs. personal account for your chosen Health Apps subcategories.
   - If using a personal developer account created after Nov 2023, plan for the mandatory **12+ testers opted in for 14 consecutive days** closed-testing gate before production access.
2. **Play App Signing & CI Release Pipeline:**
   - Generate a production upload keystore (never committed to git), store signing secrets in GitHub Actions (`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`), enroll in Play App Signing, build signed `.aab` via `./gradlew bundleRelease`, and upload `mapping.txt` for readable Play Console crash/ANR reports.
3. **Play Console Policy Forms:**
   - **Privacy Policy:** Confirm `https://kbralten.github.io/fillmark/privacy.html` is live over HTTPS (not a PDF) and matches both the Play Console listing and in-app links; confirm `fillmark@bralten.ca` is active for user/reviewer inquiries.
   - **Health Apps Declaration + Health Connect Data Type Declaration:** Declare `READ_HYDRATION` and `WRITE_HYDRATION` with clear user-facing justifications and prepare reviewer instructions (plus a short demo video showing Health Connect permission grant, logging, and sync if requested).
   - **Data Safety Form:** Ensure disclosures match actual behavior (including Android Auto Backup to the user's personal Google Account and optional on-device Health Connect sync; zero developer servers/analytics/ads).
   - **Content Rating (IARC), Target Audience, Ads Declaration (None), App Access.**
4. **Store Listing Assets & Copy:**
   - `512×512` hi-res icon (verify [docs/icon-512.png](file:///c:/Users/kevin/Dev/fillmark/docs/icon-512.png)), `1024×500` feature graphic, and 6–8 polished phone screenshots (Dashboard gauge & pacing, Quick Presets, Home Screen Widgets, Daily/Weekly Analytics, Dark Mode / Material You, Health Connect & Backup) — plus tablet/foldable screenshots to showcase the `NavigationRail` layout.
   - Short description (≤80 chars) & Full description (≤4,000 chars) including a **non-medical-device disclaimer** (e.g., *"Fillmark is for general wellness tracking only and is not a medical device"*).
5. **Pre-Launch Verification:**
   - Run Play Console **Pre-launch Report** and manual testing across real devices covering: Android 10–13 Health Connect install flow, Android 14–16 built-in Health Connect, light/dark splash screens, widget midnight rollover & Undo, large system font sizes, TalkBack navigation, and signed R8 release builds.
