# Fillmark 💧

A lightweight, local-first, privacy-respecting hydration tracker for Android.

Fillmark is designed to make fluid tracking effortless and friction-free. With one-tap logging, customizable drink presets, home screen widgets, adaptive waking-hours pacing, and seamless Health Connect sync, Fillmark helps you hit your daily hydration goals without sign-ups, subscriptions, or cloud trackers.

---

## Highlights

- 🔒 **Local-First & Zero-Auth:** No accounts, passwords, analytics, or third-party cloud servers. All personal health data stays securely on your device.
- ⚡ **Instant Logging:** Record drinks in under a second using one-tap presets with quick undo support.
- ⏱️ **Smart Waking-Hours Pacing:** Dynamic pacing tells you if you are ahead, on track, or behind schedule based on your custom wake and sleep hours.
- 📱 **Home Screen Widgets:** Log water and monitor your progress directly from your home screen using reactive Jetpack Glance widgets.
- 🔗 **Health Connect Sync:** Optional bidirectional synchronization with Android Health Connect to share data with your favorite fitness apps.
- 📊 **Detailed Trends & Analytics:** Track intake across hourly, daily, weekly, monthly, and yearly intervals.

---

## Key Features

### 💧 Fast & Flexible Logging
- **One-Tap Presets:** Instantly record drinks using preset containers (e.g., Glass, Coffee Mug, Water Bottle, Can, Tumbler).
- **Custom Presets:** Tailor presets with custom labels, container icons, fluid volumes, and hydration coefficients.
- **Hydration Coefficients:** Account for beverages with different hydration properties (e.g., water vs. coffee or tea).
- **Manual Logging:** Floating action button allows quick logging of arbitrary amounts, beverage types, and custom timestamps.
- **Instant Undo:** 4-second quick undo snackbar prevents accidental entries.

### ⏱️ Daily Dashboard & Waking Hours Pacing
- **Real-Time Progress:** High-contrast progress meter displaying daily intake against your target.
- **Adaptive Pacing Meter:** Calculates where your hydration level should be at any point in the day based on your wake-up and bedtime schedule, highlighting whether you are *ahead*, *on track*, or *behind*.
- **Historical Target Preservation:** Adjusting today's hydration target never alters past targets or historical compliance records.

### 📱 Android Home Screen Widgets
Powered by Jetpack Glance, Fillmark provides three interactive home screen widgets:
1. **Progress Meter ($2 \times 2$ / $3 \times 2$):** Circular daily progress, goal percentage, and waking-hours pacing status.
2. **Quick Log Bar ($4 \times 1$):** Instant one-tap logging buttons for your top presets right from your home screen.
3. **Full Dashboard ($4 \times 2$ / $4 \times 3$):** Combines the progress meter, favorite preset buttons, and a quick-add shortcut for manual logging.

### 📜 History & Record Management
- Chronological day-by-day logs showing timestamps, drink types, container icons, and volumes.
- Tap any record to edit its timestamp, beverage type, or volume.
- Swipe-to-delete with confirmation.

### 📈 Historical Analytics & Charts
- **Day:** 24-hour breakdown of hourly intake.
- **Week:** 7-day bar chart with daily target lines and weekly average.
- **Month / 3 Months / Year / All Time:** Long-term trends and intake summaries to help you build lasting habits.

### 🔄 Android Health Connect Integration
- Optional bidirectional synchronization with Android's official Health Connect API (`HydrationRecord`).
- Automatically updates Health Connect when you add, edit, or delete records.
- Inbound sync pulls hydration entries logged in other apps without creating duplicate records.
- Works entirely on-device; toggle sync or revoke permissions at any time.

### ⚙️ Personalization & Preferences
- Seamless switching between **Metric** ($\text{ml}$) and **Imperial** ($\text{fl oz}$). All data is stored in milliliters internally to prevent conversion drift.
- Material 3 dynamic styling with full Edge-to-Edge Android support.

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

- **UI & Layout:** 100% [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 and Navigation 3.
- **Home Screen Widgets:** [Jetpack Glance](https://developer.android.com/jetpack/compose/glance) with reactive state updates linked to database triggers.
- **Architecture:** MVVM (Model-View-ViewModel) + Repository pattern with Kotlin Coroutines and Flows.
- **Local Persistence:** [Room](https://developer.android.com/training/data-storage/room) (SQLite abstraction) for hydration records, daily targets, and presets; Jetpack DataStore for user preferences.
- **Background Sync:** Android WorkManager for asynchronous Health Connect synchronization.
- **Health Integration:** AndroidX Health Connect Client library (`androidx.health.connect:connect-client`).

### Source Structure

```text
app/src/main/kotlin/com/fillmark/app/
├── core/         # Domain models, enums (DrinkType, PresetIcon), volume & time utilities
├── data/         # Room entities, DAOs, Database definition, DataStore repository
├── domain/       # Business logic (e.g., PacingCalculator)
├── sync/         # Health Connect manager, sync worker, rationale activity
├── ui/           # Jetpack Compose UI screens (Dashboard, History, Analytics, Settings)
└── widget/       # Jetpack Glance widget providers and action callbacks
```

### Building the Project

1. **Prerequisites:**
   - [Android Studio](https://developer.android.com/studio) (Ladybug / Iguana or newer recommended).
   - JDK 17 (Gradle toolchain is configured for Java 17).
   - Android SDK with Platform 36 and Build Tools.

2. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/fillmark.git
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

4. **Run Unit Tests:**
   ```bash
   ./gradlew test
   ```

---

## Privacy & Security

Fillmark was conceived with privacy as a foundational requirement:
- **No analytics or crash-reporting telemetry.**
- **No advertising SDKs or identifiers.**
- **No external server calls**—network connectivity is never required to track hydration.
- You retain complete ownership of your data at all times.

---

## License

This project is open-source under the terms of the [GNU General Public License v3.0](https://www.gnu.org/licenses/gpl-3.0.html). See [COPYING](COPYING) for the full license text.