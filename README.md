# Kirana Khata

A free, offline Android app for shops. It covers the khata (ledger) for customers and suppliers, bills and GST invoices, stock, expenses and daily cash. Everything stays on the phone, and no account or internet is needed.

The default language is **Hinglish**, with English and Hindi options.

## Features

- **Khata:** customers and suppliers with Credit / Payment entries and automatic running balances. You can edit, back-date or delete entries, add due dates, send WhatsApp reminders, call, and share PDF statements. You can also add an opening balance with a photo of the old paper ledger.
- **Bills:** items with quantity and unit, suggestions from earlier bills, and paid / udhaar / part paid (the unpaid part goes to the khata). Totals round off, and bills can be edited for 24 hours and searched later.
- **GST invoices:** an A4 tax invoice or a small receipt. It uses the bundled CBIC HSN rate list (22.09.2025) and adds CGST + SGST on top of Rate × Qty. It also prints FSSAI, a Ship to address, and bank / UPI details.
- **Stock:** quantities drop automatically from bills. Stock in / out, history and low-stock alerts.
- **Money:** expenses by category, Galla (daily cash in / out), and reports by day, week or month.
- **Safety:** Google Drive auto-backup, a backup file with restore, and an app lock (fingerprint or PIN).

More detail is in [FEATURES.md](FEATURES.md), [SCREENS.md](SCREENS.md) and [TECH.md](TECH.md).

## Tech

Kotlin, Jetpack Compose (Material 3) and Room (SQLite). It runs on Android 8.0 and newer (minSdk 26, targetSdk 35). Money is stored as whole paise and GST rates in thousandths of a percent, so there is no floating-point rounding.

## Build

1. Install Android Studio (or JDK 17+ and the Android SDK).
2. Clone the repo and open it, or run:

   ```bash
   ./gradlew assembleDebug
   ```

The debug build installs as a separate app called **"Kirana TEST"** with its own empty data, so it never touches a real shop's khata.

Release builds need your own signing key. Create a `keystore.properties` file with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. It is git-ignored, so never commit it.

## Tests

```bash
./gradlew testDebugUnitTest            # money and GST maths on the computer
./gradlew connectedDebugAndroidTest    # flows on a phone (uses in-memory databases)
```

## Contributing

Contributions are welcome. Fork the repo, make your change on a branch, and open a pull request.

- Keep the app simple for a busy shopkeeper: big buttons, few steps, Hinglish first.
- Add every new text in all three languages (`values`, `values-b+hi+Latn`, `values-hi`).
- Money stays in paise (`Long`), never `Double`.
- Changing the database means adding a Room migration and a migration test.

## License

[MIT](LICENSE)
