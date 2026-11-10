# 💰 Personal Expense Tracker and Budget Planning

A full-featured, offline-first **Android app** for tracking income and expenses, planning monthly budgets, reaching savings goals and never missing a bill. Data lives on your device (Room / SQLite) and can optionally be backed up to your own private Firebase account.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/language-Java%2017-007396?logo=openjdk&logoColor=white)
![Min SDK](https://img.shields.io/badge/minSdk-23-blue)
![Target SDK](https://img.shields.io/badge/targetSdk-33-blue)
![Build](https://img.shields.io/badge/build-Gradle%208.0-02303A?logo=gradle&logoColor=white)
![Backend](https://img.shields.io/badge/cloud-Firebase%20(optional)-FFCA28?logo=firebase&logoColor=black)

> **Currency:** amounts are in Bangladeshi Taka (৳) and stored as whole *paisa* (integers) with `BigDecimal` parsing, so there are no floating-point rounding errors.

---

## 📑 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Getting Started](#-getting-started)
- [Firebase Setup (Optional)](#-firebase-setup-optional)
- [Running the Tests](#-running-the-tests)
- [Project Structure](#-project-structure)
- [Architecture & Design Decisions](#-architecture--design-decisions)
- [Security & Privacy](#-security--privacy)
- [Permissions](#-permissions)
- [Author](#-author)
- [License](#-license)

---

## ✨ Features

### 🧾 Transactions
- Add, edit and delete **income** and **expense** transactions with an amount, category, date and optional note.
- Instant **undo** after deleting.
- Live **current balance**, total income and total expenses on the dashboard.
- Built-in categories:
  - **Income:** Salary, Business, Freelance, Gift, Investment, Other Income
  - **Expense:** Food, Transport, Rent, Utilities, Education, Health, Shopping, Entertainment, Other Expense

### 🎯 Budgets
- Set an overall **monthly budget** plus **per-category budgets**, month by month.
- Progress bars show spent / remaining / % used.
- **Alerts at 80 % and 100 %** of a budget, and an "overspent by …" warning.
- **Copy last month's budgets** into the current month in one tap.

### 📊 Reports & Insights
- Pick a **calendar month** or a **custom date range** (presets: last 7 / 30 / 90 days, this year).
- Charts (via MPAndroidChart): **donut chart** of expenses by category, **income vs. expenses** bar chart by month, and a **spending trend** line (daily / weekly / monthly).
- Net cash flow, savings rate, and comparison with the **previous period**.
- Top spending categories and largest transactions.
- **Budget vs. actual** comparison for whole-month ranges.
- Export any report as a **PDF financial report**.
- Dedicated landscape layouts for the dashboard and reports.

### 🐷 Savings Goals
- Create goals with a target amount and optional deadline.
- Record **contributions** and review the full contribution history.
- See progress, remaining amount, days left and a **suggested monthly saving** to hit the deadline.
- Archive / restore goals; the dashboard shows totals and the next deadline.

### 🔁 Recurring Transactions
- Schedule **daily, weekly or monthly** income/expenses (salary, rent, subscriptions…) with an optional end date.
- Posted automatically in the background by WorkManager, and **each occurrence is posted exactly once**.
- Correct month-end handling (a rule starting on the 31st gives Jan 31 → Feb 28/29 → Mar 31, without drifting).
- Pause / resume, and a forecast of upcoming income and expenses on the dashboard.

### 🔔 Bills & Reminders
- Track bills with a due date, amount, category and "remind me N days before".
- **Notifications** for upcoming and overdue bills.
- **Mark as paid** to record the payment as a transaction (and mark unpaid to reverse it).
- Pick the payee straight from your **Contacts**.
- Attach a **receipt photo** from the gallery or the camera (no `CAMERA` permission needed).

### 🔐 Security
- Optional **PIN lock** (4–6 digits), stored only as a salted **PBKDF2-HMAC-SHA256** hash.
- **Fingerprint / face unlock** via AndroidX Biometric.
- Wrong-PIN **lock-out** with exponential back-off (starts after 5 failures: 30 s, doubling up to 5 min).
- **Hide app content** in the recents screen and screenshots.

### ☁️ Backup, Restore & Export
- **Local full backup** to a single JSON file and **strict restore**: the whole file is validated before anything touches the database, so a damaged file can never leave the app half-restored.
- **CSV export** of transactions, budgets, goals, contributions, recurring rules and bills (UTF-8 with BOM so Excel shows Bengali text correctly).
- **Cloud backup/restore with Firebase** (email + password sign-in): manual, or automatic once a day when something changed, with **conflict detection** so one device never silently overwrites another's data.
- A safety copy is saved automatically before every restore.
- Backup / restore run as a **foreground service** with a progress notification.
- Dashboard banner when the device is **offline** or the **battery is low**.

### 🌍 Extras
- **Exchange rates** screen (live rates from `open.er-api.com`, cached for offline use) showing your balance in US$.
- **Four themes:** White, Blue, Red and Dark.
- Animated **welcome screen** (always under 2 seconds).
- Right-to-left layouts supported (`supportsRtl`).

---

## 🛠 Tech Stack

| Area | Technology |
|---|---|
| Language | Java 17 |
| UI | AndroidX AppCompat, Material Components, ConstraintLayout, RecyclerView, CardView, **View Binding** |
| Local database | **Room 2.5.2** (SQLite) with exported schemas and tested migrations (v1 → v4) |
| Background work | **WorkManager 2.8.1** (recurring postings, bill reminders, daily cloud sync) |
| Charts | [MPAndroidChart v3.1.0](https://github.com/PhilJay/MPAndroidChart) (via JitPack) |
| Security | AndroidX Biometric 1.1.0, PBKDF2-HMAC-SHA256 |
| Cloud (optional) | Firebase **Authentication**, **Cloud Firestore**, **Realtime Database**, **Cloud Storage** (BoM 32.2.3) |
| Images | AndroidX Activity (photo picker / `TakePicture`), ExifInterface |
| Networking | `HttpURLConnection` + `org.json` for the exchange-rate call (no extra library) |
| Build | Gradle 8.0, Android Gradle Plugin 8.0.2, `compileSdk` / `targetSdk` 33, `minSdk` 23 |
| Testing | JUnit 4, `org.json`, AndroidX Test, Room Testing |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Flamingo (2022.2.1) or newer
- **JDK 17** (bundled with recent Android Studio versions)
- **Android SDK Platform 33**
- An emulator or physical device running **Android 6.0 (API 23) or higher**

### Build and run

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/Personal-Expense-Tracker-and-Budget-Planning.git
cd Personal-Expense-Tracker-and-Budget-Planning

# 2. Build a debug APK
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug

# 3. Install it on a connected device / running emulator
./gradlew installDebug
```

Or simply open the project folder in **Android Studio**, let Gradle sync, and press **Run ▶**.

> ℹ️ The app builds and runs **without any Firebase configuration**. Cloud features are simply switched off and the Cloud screen shows a "not set up" notice. Everything else works fully offline.

---

## 🔥 Firebase Setup (Optional)

Only needed if you want cloud backup. You use **your own** free Firebase project; your data never touches anyone else's.

1. Create a project in the [Firebase console](https://console.firebase.google.com/).
2. **Add an Android app** with the package name `com.example.expensetracker`.
3. Download **`google-services.json`** and place it in the `app/` folder.
   The Gradle build applies the Google Services plugin automatically when this file exists.
4. In the console, enable:
   - **Authentication → Sign-in method → Email/Password**
   - **Firestore Database**
   - **Realtime Database**
   - **Storage**
5. Deploy the included security rules (via the console, or the [Firebase CLI](https://firebase.google.com/docs/cli)):

   | File | Service |
   |---|---|
   | `firestore.rules` | Cloud Firestore |
   | `database.rules.json` | Realtime Database |
   | `storage.rules` | Cloud Storage |

6. Rebuild and run the app, then open **⋮ → Cloud account (Firebase)** to sign up and back up.

> ⚠️ `app/google-services.json` is listed in `.gitignore` on purpose. Don't commit your own Firebase config to a public repository.

### What gets stored in the cloud

| Service | Path | Contents |
|---|---|---|
| Firestore | `users/{uid}/…` | Mirror of every table: transactions, budgets, savings goals, contributions, recurring rules, bills |
| Realtime Database | `users/{uid}/profile`, `users/{uid}/syncLog` | Last-seen / last-backup info and a short sync log |
| Cloud Storage | `users/{uid}/receipts/{file}` | Receipt photos (JPEG, max 5 MB) |

---

## 🧪 Running the Tests

The project has **245 JVM unit tests** (business logic is deliberately kept in pure-Java classes) and **16 instrumented tests** for the database, migrations and backup/restore.

```bash
# Unit tests: fast, no device needed
./gradlew testDebugUnitTest

# Instrumented tests: need a running emulator or connected device
./gradlew connectedDebugAndroidTest
```

| Area | Covered by |
|---|---|
| Money parsing & formatting | `MoneyUtilsTest`, `MoneyFormatStage5Test` |
| Budgets & alerts | `BudgetCalculatorTest`, `BudgetValidatorTest`, `BudgetAlertsTest` |
| Reports, periods & PDF | `ReportCalculatorTest`, `ReportPeriodTest`, `ReportFormatTest`, `PdfReportModelTest`, `PdfReportBuilderTest` |
| Goals, recurring rules & bills | `GoalCalculatorTest`, `RecurrenceCalculatorTest`, `BillCalculatorTest`, `PlanValidatorTest`, `PlanDatesTest` |
| Security | `PinPolicyTest`, `PinHasherTest` |
| Backup / CSV / cloud logic | `BackupJsonTest`, `BackupJsonReceiptTest`, `CsvExportTest`, `CsvWriterTest`, `CloudDocsTest`, `ReceiptNamesTest` |
| Misc | `ThemeManagerTest`, `ExchangeRatesTest`, `DeviceStatusTest`, `MonthUtilsTest`, resource checks |
| Database (instrumented) | `Stage4DatabaseTest`, `MigrationTest`, `ReportsDaoTest`, `BackupRestoreTest` |

---

## 📁 Project Structure

```
Personal-Expense-Tracker-and-Budget-Planning/
├── app/
│   ├── build.gradle                  # module config & dependencies
│   ├── google-services.json          # your Firebase config (git-ignored, optional)
│   ├── schemas/                      # exported Room schemas (v1, v4) used by migration tests
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/example/expensetracker/
│       │   └── res/                  # layouts (+ landscape), menus, strings, themes, drawables, xml
│       ├── test/                     # JVM unit tests
│       └── androidTest/              # instrumented tests
├── firestore.rules                   # Firestore security rules
├── database.rules.json               # Realtime Database rules
├── storage.rules                     # Cloud Storage rules
├── build.gradle · settings.gradle · gradle.properties
└── gradlew · gradlew.bat · gradle/wrapper/
```

### Source code at a glance (`com.example.expensetracker`)

| Layer | Classes |
|---|---|
| **Screens** | `SplashActivity`, `MainActivity` (dashboard), `AddTransactionActivity`, `BudgetActivity`, `ReportsActivity`, `GoalsActivity`, `GoalDetailActivity`, `RecurringActivity`, `BillsActivity`, `SettingsActivity`, `CloudAccountActivity`, `LockActivity`, `AboutActivity` |
| **Data (Room)** | `AppDatabase`, entities (`Transaction`, `Budget`, `SavingsGoal`, `GoalContribution`, `RecurringRule`, `RecurringPosting`, `Bill`), `*Dao` interfaces, `*Repository` classes |
| **Business logic (pure Java)** | `BudgetCalculator`, `BudgetAlerts`, `ReportCalculator`, `DashboardCalculator`, `GoalCalculator`, `RecurrenceCalculator`, `BillCalculator`, `MoneyUtils`, `MonthUtils`, `PinPolicy`, `PinHasher` |
| **Background work** | `RecurringWorker`, `BillReminderWorker`, `CloudSyncWorker`, `BackupService`, `BackgroundWork`, `NotificationHelper` |
| **Backup & export** | `BackupJson`, `BackupRepository`, `CsvExport`, `CsvWriter`, `PdfReportBuilder`, `PdfReportRenderer`, `ReportPdfExporter`, `ExportController` |
| **Cloud** | `CloudSync`, `CloudDocs`, `CloudConfig`, `CloudErrors`, `ReceiptSync`, `ReceiptStore` |
| **Settings & UI** | `AppSettings`, `AppLock`, `ThemeManager`, `BaseActivity`, adapters and dialog helpers |
| **Network** | `ExchangeRateClient`, `ExchangeRates` |

---

## 🧠 Architecture & Design Decisions

- **Offline-first.** Room is the single source of truth on the device; the cloud is only a mirror. The app is fully usable with no account and no internet.
- **Exact money.** Amounts are parsed with `BigDecimal` and stored as `long` paisa (max ৳ 999,999,999.99).
- **Testable logic.** Calculators, validators and parsers have no Android dependencies, which is why 245 tests run on the plain JVM in seconds.
- **Safe database evolution.** Room schemas are exported and every migration is verified by instrumented tests, so user data survives upgrades.
- **All-or-nothing restore.** Backups are fully validated (format marker, version, field types, ranges, unique ids, foreign references) before any write, and a safety copy is taken first.
- **No silent overwrites in the cloud.** Each upload carries a version stamp; a non-forced upload is refused on conflict. Half-written uploads are marked `uploading` and rejected on download.
- **Free-tier friendly.** Only changed rows are uploaded, and nothing is sent when a fingerprint shows the data is unchanged.
- **Idempotent recurring postings.** A unique index on `(ruleId, occurrenceMillis)` guarantees a scheduled date can never be posted twice.

---

## 🔒 Security & Privacy

- Your **PIN is never uploaded** and is excluded from Android auto-backup and from the app's own backup file (`security_prefs` is excluded in `backup_rules.xml`).
- The PIN is **unrecoverable by design**: if you forget it, clear the app's data from Android settings (so keep a backup).
- **Firestore, Realtime Database and Storage rules** restrict every user to *their own* `users/{uid}` data only; everything else is denied by default. Receipt uploads are limited to JPEG files under 5 MB.
- Receipt photos are **re-encoded and scaled** before storage, which strips location (EXIF) data. They live in app-private storage and are shared only through a `FileProvider` with restricted paths.
- The only third-party HTTP call is the public exchange-rate API, which sends no personal data.

---

## 📱 Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Firebase backup and exchange rates |
| `POST_NOTIFICATIONS` | Bill reminders and budget alerts (runtime prompt on Android 13+) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Progress notification during backup / restore |

The camera hardware is optional (`required="false"`); photos are taken by the system camera app, so no `CAMERA` permission is requested.

---


## 👤 Author

**Krishno Mondol**
Department of Computer Science and Engineering, Khulna University of Engineering & Technology (KUET), Khulna, Bangladesh

*Built as an academic project.*

---


## 📄 License

This project is licensed under the MIT License.

You are free to use, modify, and distribute this software
under the terms and conditions of the MIT License.

See the [LICENSE](LICENSE) file for more details.


