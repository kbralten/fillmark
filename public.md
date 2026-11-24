# Fillmark — Public Release Readiness Evaluation

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
