# Fillmark 💧

A lightweight, local-first, privacy-respecting drink and water tracker for Android.

Fillmark is designed to make drink tracking effortless and friction-free. With one-tap logging, customizable drink presets, home screen widgets, adaptive waking-hours pacing, and seamless Health Connect sync, Fillmark helps you hit your daily drinking goals without sign-ups, subscriptions, or cloud trackers.

---

## Highlights

- 🔒 **Local-First & Zero-Auth:** No accounts, passwords, analytics, or developer/third-party cloud servers. All personal health data stays on your device (and, if Android Auto Backup is enabled on your phone, backs up solely to your own personal Google Account).
- ⚡ **Instant Logging:** Record drinks in under a second using one-tap presets with quick undo support.
- ⏱️ **Smart Waking-Hours Pacing:** Dynamic pacing tells you if you are ahead, on track, or behind schedule based on your custom wake and sleep hours.
- 📱 **Home Screen Widgets & Shortcuts:** Log water and monitor your progress from your home screen with Jetpack Glance widgets (including quick Undo), a Quick Settings tile, and dynamic App Shortcuts.
- 🔗 **Health Connect Sync:** Optional bidirectional synchronization with Android Health Connect to share data with your favorite fitness apps.
- 📊 **Detailed Trends & Analytics:** Track intake across daily (cumulative & rate), weekly, monthly, 3-month, yearly, and all-time intervals with goal streaks and drink breakdowns.
- 💾 **Local Backup & Export:** Export and restore full local backups as JSON, or export drink logs to CSV at any time.

---

## Key Features

### 💧 Fast & Flexible Logging
- **One-Tap Presets:** Instantly record drinks using preset containers (e.g., Glass, Coffee Mug, Water Bottle, Can, Tumbler).
- **Custom Presets & Labels:** Tailor presets with custom labels, container icons, fluid volumes, drink categories, and colors; custom preset and manual drink labels are preserved across logs, history, and JSON backups.
- **Custom Hydration Factors:** Account for beverages with different hydration efficiency (e.g., water vs. coffee or tea) with customizable multipliers (`0.1x–2.0x`), plus a global toggle in Settings to count 1:1 raw volume instead.
- **Manual Logging:** Floating action button allows quick logging of arbitrary amounts, custom names, beverage types, and past timestamps.
- **Quick Settings Tile & App Shortcuts:** Log your top preset directly from Android's Quick Settings shade or long-press launcher shortcuts.
- **Instant Undo:** 4-second quick undo snackbar prevents accidental entries.

### ⏱️ Daily Dashboard & Waking Hours Pacing
- **First-Run Onboarding & Goal Estimator:** Guided 3-step setup with locale-aware unit defaults, an optional weight/activity-based Daily Goal Estimator, waking window configuration, and optional Health Connect connection (automatically skipped for existing users).
- **Real-Time Progress & Celebration:** High-contrast progress meter displaying daily intake against your target (or standalone consumption if no target is set), with haptic feedback and a "Goal Reached!" milestone highlight at 100%.
- **Per-Day Goal Override:** Tap today's target on the Dashboard to adjust your goal for today only without altering past or future days.
- **Adaptive Pacing Meter:** Calculates where your drinking level should be at any point in the day based on your wake-up and bedtime schedule, highlighting whether you are *ahead*, *on track*, or *behind*.
- **Historical Target Preservation:** Adjusting your default target in Settings preserves past daily targets and historical compliance records.

### 📱 Android Home Screen Widgets
Powered by Jetpack Glance, Fillmark provides three interactive home screen widgets (pin-able directly from Settings on supported launchers):
1. **Progress Meter (4×1):** Luminous horizontal progress bar, goal percentage, consumed/target volume, and waking-hours pacing pill.
2. **Quick Log Bar (4×1):** Instant one-tap logging buttons for your top presets right from your home screen, with 30-second Undo support.
3. **Full Dashboard (4×2):** Combines the luminous progress bar and pacing status pill with your favorite preset buttons and quick Undo support.

### 📜 History & Record Management
- Chronological day-by-day logs showing timestamps, custom drink labels, beverage categories, container icons, and volumes.
- Search by drink name/category and filter logs by drink type or calendar date.
- Tap any record to edit its timestamp, drink name, beverage category, hydration factor, or volume (or delete directly from the edit dialog).
- Swipe-to-delete (with confirmation dialog) or one-tap delete with an instant Undo snackbar.

### 📈 Historical Analytics & Charts
- **Day:** Cumulative intake and hourly intake rate line charts with daily target and waking-hours pacing reference lines.
- **Week:** 7-day bar chart with daily target lines, weekly average, and tap-a-bar day inspection.
- **Month / 3 Months / Year / All Time:** Long-term trends, current & best goal streaks, and drink type breakdowns to help you build lasting habits.

### 🔄 Android Health Connect Integration
- Optional bidirectional synchronization with Android's official Health Connect API (`HydrationRecord`).
- Automatically updates Health Connect when you add, edit, or delete records.
- Inbound sync pulls hydration entries logged in other apps without creating duplicate records.
- Works entirely on-device; toggle sync or revoke permissions at any time.

### ⚙️ Personalization, Localization & Local Data
- Seamless switching between **Metric** (ml) and **Imperial** (fl oz) with locale-aware decimal formatting and comma/dot decimal input support. All data is stored in milliliters internally to prevent conversion drift.
- **Languages:** Localized in English, Spanish (`es`), French (`fr`), and German (`de`) with Android per-app language support (`locales_config`).
- **Appearance:** Light, Dark, and System theme modes with optional Material You dynamic color (Android 12+), full Edge-to-Edge support, and a responsive `NavigationRail` layout on tablets and foldables.
- **Local Data Tools:** Export/restore full local JSON backups, export drink logs to CSV, or reset and clear all local data in Settings.

---

## Platform & Compatibility

- **OS Version:** Android 10 (API level 29) or higher.
- **Health Connect:**
  - Android 14+: Built directly into the Android system settings.
  - Android 10–13: Supported via the [Health Connect by Android](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) app from Google Play.

---

## Developer Hints & Building

Fillmark is built with modern Android development standards and clean architectural patterns.

### Architecture & Tech Stack

- **UI & Layout:** 100% [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 (dynamic color, bottom navigation bar, and responsive tablet `NavigationRail`).
- **Home Screen Widgets:** [Jetpack Glance](https://developer.android.com/jetpack/compose/glance) with reactive state updates linked to database triggers.
- **Architecture:** MVVM (Model-View-ViewModel) + Repository pattern with Kotlin Coroutines and Flows.
- **Local Persistence:** [Room](https://developer.android.com/training/data-storage/room) (SQLite abstraction) for hydration records, daily targets, and presets; Jetpack DataStore for user preferences; `kotlinx.serialization` for local JSON backup/restore.
- **Background Sync:** Android WorkManager for asynchronous Health Connect synchronization.
- **Health Integration:** AndroidX Health Connect Client library (`androidx.health.connect:connect-client`).

### Source Structure

```text
app/src/main/kotlin/com/fillmark/app/
├── core/         # Domain models, enums (DrinkType, PresetIcon), shortcuts, volume & time utilities
├── data/         # Room entities, DAOs, Database definition, DataStore repository, JSON/CSV backup
├── domain/       # Business logic (PacingCalculator, DailyGraphCalculator, AnalyticsInsightsCalculator)
├── sync/         # Health Connect manager, sync worker, rationale activity
├── ui/           # Jetpack Compose UI screens (Dashboard, History, Analytics, Settings, Onboarding)
└── widget/       # Jetpack Glance widget providers, action callbacks, and Quick Settings tile
```

### Building the Project

1. **Prerequisites:**
   - [Android Studio](https://developer.android.com/studio) (Ladybug / Iguana or newer recommended).
   - JDK 17 (Gradle toolchain is configured for Java 17).
   - Android SDK with Platform 36 and Build Tools.

2. **Clone the repository:**
   ```bash
   git clone https://github.com/kbralten/fillmark.git
   cd fillmark
   ```

3. **Build the debug APK:**
   - **Linux / macOS:**
     ```bash
     ./gradlew assembleDebug
     ```
   - **Windows:**
     ```powershell
     .\gradlew.bat assembleDebug
     ```

4. **Run Tests & Verification Checks:**
   - **Unit, Room Migration & Robolectric UI Tests + Lint:**
     ```bash
     ./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintRelease
     ```
   - **Instrumented Tests (requires connected device or emulator):**
     ```bash
     ./gradlew connectedDebugAndroidTest
     ```

---

## Privacy & Security

Fillmark was conceived with privacy as a foundational requirement:
- **No analytics or crash-reporting telemetry.**
- **No advertising SDKs or identifiers.**
- **No developer or third-party cloud servers**—network connectivity is never required to track hydration.
- **Android Cloud Auto Backup & Device Transfer:** Fillmark supports Android's built-in Auto Backup so your drink logs, presets, and settings can be restored across devices. Backups are encrypted and stored solely within **your own personal Google Account**—nothing leaves your Google Account or is ever sent to Fillmark or any third-party server.
- You retain complete ownership of your data at all times. Read the full [Privacy Policy](https://kbralten.github.io/fillmark/privacy.html).

---

## License

This project is open-source under the terms of the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html). See [COPYING](COPYING) for the full license text.